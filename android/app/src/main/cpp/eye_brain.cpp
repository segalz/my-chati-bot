#include "eye_brain.hpp"

#include <android/log.h>

#include <algorithm>
#include <atomic>
#include <chrono>
#include <cmath>
#include <vector>

#include "llama.h"

namespace chati {
namespace {

constexpr const char *kTag = "ChatiBrain";
constexpr size_t kMaxSentenceBytes = 300;  // bytes as received from JNI (modified UTF-8)
constexpr int kMaxValueTokens = 8;
// Story context window kept in the KV cache (see infer()).
constexpr size_t kMaxStorySentences = 8;
constexpr int kMaxStoryTokens = 360;
constexpr size_t kReseedSentences = 3;
// Said after the newest sentence when earlier story sentences are in context; dropped afterwards.
constexpr const char *kStoryHint = "\n(The earlier sentences are context. Choose for the last sentence.)";

// Prompt v4 from the Stage 2B benchmark (plan/eyes/evidence/model-benchmark.md), unchanged.
constexpr const char *kSystemPrompt = R"(You control the eyes of a small desk companion robot.
For each sentence, choose the eye expression and where to look.

expression: HAPPY, SAD, ANGRY, SURPRISED, SLEEPY, BORED, WINK_LEFT, WINK_RIGHT or NEUTRAL. Mirror the speaker's feeling.
Use NONE when the eyes should not react: plain facts, numbers, schedules and chores.
look: left, right, up or down only when the sentence asks to look there, otherwise center.

Examples:
Sentence: I passed all my exams!
{"expression":"HAPPY","look":"center"}
Sentence: My best friend is moving far away.
{"expression":"SAD","look":"center"}
Sentence: Glance up at the window.
{"expression":"NEUTRAL","look":"up"}
Sentence: The oven needs to be cleaned.
{"expression":"NONE","look":"center"}
Sentence: You broke my favorite mug, I'm so mad!
{"expression":"ANGRY","look":"center"}
Sentence: The train leaves at 7:15.
{"expression":"NONE","look":"center"})";

// Marker splitting the templated prompt into the cached prefix and the per-sentence suffix.
constexpr const char *kMarker = "\x01SENTENCE\x01";

constexpr const char *kExpressionGrammar =
    R"(root ::= "NEUTRAL" | "HAPPY" | "SAD" | "ANGRY" | "SURPRISED" | "SLEEPY" | "BORED" | "WINK_LEFT" | "WINK_RIGHT" | "NONE")";
const char *const kLabels[] = {"NEUTRAL", "HAPPY", "SAD", "ANGRY", "SURPRISED", "SLEEPY", "BORED",
                               "WINK_LEFT", "WINK_RIGHT", "NONE"};
constexpr const char *kJsonOpen = R"({"expression":")";
constexpr const char *kJsonClose = R"("})";  // appended, never generated

using Clock = std::chrono::steady_clock;

int64_t now_ms() {
    return std::chrono::duration_cast<std::chrono::milliseconds>(Clock::now().time_since_epoch()).count();
}

std::vector<llama_token> tokenize(const llama_vocab *vocab, const std::string &text, bool add_bos, bool special) {
    int n = -llama_tokenize(vocab, text.data(), static_cast<int32_t>(text.size()), nullptr, 0, add_bos, special);
    std::vector<llama_token> tokens(n);
    if (n > 0) {
        llama_tokenize(vocab, text.data(), static_cast<int32_t>(text.size()), tokens.data(), n, add_bos, special);
    }
    return tokens;
}

std::string piece(const llama_vocab *vocab, llama_token token) {
    char buf[64];
    const int n = llama_token_to_piece(vocab, token, buf, sizeof(buf), 0, false);
    return n > 0 ? std::string(buf, n) : std::string();
}

bool decode(llama_context *ctx, std::vector<llama_token> &tokens) {
    if (tokens.empty()) return true;
    return llama_decode(ctx, llama_batch_get_one(tokens.data(), static_cast<int32_t>(tokens.size()))) == 0;
}

/// Greedy token under `grammar`. Like llama.cpp's common_sampler: try the unconstrained argmax
/// first and run the grammar over the whole (262k-token) vocabulary only if that token is invalid.
llama_token grammar_greedy(llama_context *ctx, const llama_vocab *vocab, llama_sampler *grammar) {
    const float *logits = llama_get_logits_ith(ctx, -1);
    const int n_vocab = llama_vocab_n_tokens(vocab);
    llama_token best = 0;
    for (llama_token t = 1; t < n_vocab; ++t) {
        if (logits[t] > logits[best]) best = t;
    }
    llama_token_data single{best, logits[best], 0.0f};
    llama_token_data_array one{&single, 1, -1, false};
    llama_sampler_apply(grammar, &one);
    if (one.data[0].logit != -INFINITY) return best;

    std::vector<llama_token_data> all(n_vocab);
    for (llama_token t = 0; t < n_vocab; ++t) all[t] = {t, logits[t], 0.0f};
    llama_token_data_array cur{all.data(), all.size(), -1, false};
    llama_sampler_apply(grammar, &cur);
    llama_token chosen = -1;
    float chosen_logit = -INFINITY;
    for (size_t i = 0; i < cur.size; ++i) {
        if (cur.data[i].logit > chosen_logit) {
            chosen_logit = cur.data[i].logit;
            chosen = cur.data[i].id;
        }
    }
    return chosen;
}

/// Classifies the expression straight from the logits: greedy tokens under `grammar`, but stops
/// as soon as the text so far is the prefix of exactly one label (usually right after the first
/// token, which comes from the prompt's own logits — no extra decode). Only tokens that leave
/// several labels open (e.g. "W" for WINK_LEFT/WINK_RIGHT) are decoded to read the next step.
bool classify(llama_context *ctx, const llama_vocab *vocab, std::string &out, int &sampled) {
    llama_sampler *g = llama_sampler_init_grammar(vocab, kExpressionGrammar, "root");
    if (g == nullptr) return false;
    bool ok = false;
    for (int i = 0; i < kMaxValueTokens; ++i) {
        const llama_token token = grammar_greedy(ctx, vocab, g);
        if (token < 0) break;
        llama_sampler_accept(g, token);
        if (llama_vocab_is_eog(vocab, token)) break;  // grammar complete without a unique match
        out += piece(vocab, token);
        ++sampled;
        const char *only = nullptr;
        int matches = 0;
        for (const char *label : kLabels) {
            if (std::string(label).rfind(out, 0) == 0) {
                ++matches;
                only = label;
            }
        }
        if (matches == 1) {
            out = only;  // unique: complete the label without decoding the rest
            ok = true;
            break;
        }
        if (matches == 0) break;
        std::vector<llama_token> one{token};
        if (!decode(ctx, one)) break;
    }
    llama_sampler_free(g);
    return ok;
}

}  // namespace

EyeBrain &EyeBrain::instance() {
    static EyeBrain brain;
    return brain;
}

bool EyeBrain::abort_check(void *self) {
    const auto *brain = static_cast<EyeBrain *>(self);
    const int64_t deadline = brain->deadline_ms_.load();
    return brain->cancelled() || (deadline != 0 && now_ms() > deadline);
}

bool EyeBrain::loaded() {
    std::lock_guard<std::mutex> lock(mutex_);
    return ctx_ != nullptr;
}

void EyeBrain::release() {
    std::lock_guard<std::mutex> lock(mutex_);
    release_locked();
}

void EyeBrain::set_threads(int threads, int threads_batch) {
    std::lock_guard<std::mutex> lock(mutex_);
    if (ctx_ == nullptr) return;
    llama_set_n_threads(ctx_, threads, threads_batch);
    __android_log_print(ANDROID_LOG_INFO, kTag, "threads set: %d (batch %d)", threads, threads_batch);
}

void EyeBrain::release_locked() {
    if (ctx_ != nullptr) llama_free(ctx_);
    if (model_ != nullptr) llama_model_free(model_);
    ctx_ = nullptr;
    model_ = nullptr;
    prefix_tokens_ = 0;
    story_tokens_ = 0;
    story_generation_ = -1;
    story_sentences_.clear();
}

void EyeBrain::reset_story_locked() {
    if (ctx_ != nullptr) llama_memory_seq_rm(llama_get_memory(ctx_), 0, prefix_tokens_, -1);
    story_tokens_ = 0;
    story_generation_ = -1;
    story_sentences_.clear();
}

bool EyeBrain::load(const std::string &model_path, int threads, std::string &error) {
    std::lock_guard<std::mutex> lock(mutex_);
    if (ctx_ != nullptr) return true;
    const int64_t start = now_ms();
    running_generation_ = INT64_MAX;  // loading is never cancelled by sentence generations
    static std::once_flag backend_once;
    std::call_once(backend_once, [] { llama_backend_init(); });

    llama_model_params mparams = llama_model_default_params();
    model_ = llama_model_load_from_file(model_path.c_str(), mparams);
    if (model_ == nullptr) {
        error = "cannot load model " + model_path;
        return false;
    }

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = 1024;
    cparams.n_batch = 512;
    cparams.n_ubatch = 512;
    cparams.n_threads = threads;
    cparams.n_threads_batch = threads;
    cparams.swa_full = true;  // Gemma 3 sliding window: needed to drop just the request tokens
    cparams.abort_callback = abort_check;
    cparams.abort_callback_data = this;
    ctx_ = llama_init_from_model(model_, cparams);
    if (ctx_ == nullptr) {
        release_locked();
        error = "cannot create context";
        return false;
    }

    // Template once with a marker in place of the sentence, then split around it.
    const llama_chat_message messages[] = {
        {"system", kSystemPrompt},
        {"user", "Sentence:\x01SENTENCE\x01"},
    };
    const char *tmpl = llama_model_chat_template(model_, nullptr);
    std::vector<char> buf(8192);
    int n = llama_chat_apply_template(tmpl, messages, 2, true, buf.data(), static_cast<int32_t>(buf.size()));
    if (n > static_cast<int>(buf.size())) {
        buf.resize(n);
        n = llama_chat_apply_template(tmpl, messages, 2, true, buf.data(), static_cast<int32_t>(buf.size()));
    }
    const std::string templated = n > 0 ? std::string(buf.data(), n) : std::string();
    const size_t at = templated.find(kMarker);
    if (at == std::string::npos) {
        release_locked();
        error = "chat template failed";
        return false;
    }
    const std::string prefix = templated.substr(0, at);
    prompt_suffix_ = templated.substr(at + std::string(kMarker).size()) + kJsonOpen;

    const llama_vocab *vocab = llama_model_get_vocab(model_);
    std::vector<llama_token> prefix_tokens = tokenize(vocab, prefix, true, true);
    deadline_ms_ = 0;
    if (!decode(ctx_, prefix_tokens)) {
        release_locked();
        error = "prefix decode failed";
        return false;
    }
    prefix_tokens_ = static_cast<int>(prefix_tokens.size());

    // Once per load: which tokens the expression grammar allows first, and which of them lead to
    // "NONE". Lets infer() report the gap to "no reaction" without scanning the whole vocabulary.
    first_tokens_.clear();
    first_leads_to_none_.clear();
    if (llama_sampler *g = llama_sampler_init_grammar(vocab, kExpressionGrammar, "root")) {
        const int n_vocab = llama_vocab_n_tokens(vocab);
        std::vector<llama_token_data> all(n_vocab);
        for (llama_token tok = 0; tok < n_vocab; ++tok) all[tok] = {tok, 0.0f, 0.0f};
        llama_token_data_array cur{all.data(), all.size(), -1, false};
        llama_sampler_apply(g, &cur);
        for (size_t i = 0; i < cur.size; ++i) {
            if (cur.data[i].logit == -INFINITY) continue;
            const std::string p = piece(vocab, cur.data[i].id);
            first_tokens_.push_back(cur.data[i].id);
            first_leads_to_none_.push_back(!p.empty() && std::string("NONE").rfind(p, 0) == 0);
        }
        llama_sampler_free(g);
    }
    __android_log_print(ANDROID_LOG_INFO, kTag, "model loaded in %lld ms, prefix %d tokens, threads %d, %zu first tokens",
                        static_cast<long long>(now_ms() - start), prefix_tokens_, threads, first_tokens_.size());
    return true;
}

BrainResult EyeBrain::infer(const std::string &sentence, int timeout_ms, int64_t generation, bool continue_story) {
    std::lock_guard<std::mutex> lock(mutex_);
    BrainResult r;
    const int64_t start = now_ms();
    if (ctx_ == nullptr) {
        r.error = "model not loaded";
        return r;
    }
    if (sentence.empty() || sentence.size() > kMaxSentenceBytes) {
        reset_story_locked();  // a skipped sentence must not leave the story context silently gapped
        r.error = "sentence must be 1.." + std::to_string(kMaxSentenceBytes) + " bytes";
        return r;
    }
    running_generation_ = generation;
    const llama_vocab *vocab = llama_model_get_vocab(model_);

    // KV layout: [system prompt + "Sentence:"][story so far][this request]. The story so far is the
    // earlier sentences of the same story (same generation) as " A", "\nSentence: B", … — exactly
    // the tokens decoded for them before, so continuing a story only decodes the new sentence. The
    // first sentence of a story and every single sentence use the plain layout of the benchmark.
    // Sentences are user text: tokenized WITHOUT special-token parsing, so they cannot inject
    // template control tokens. Only the fixed hint and template suffix may contain special tokens.
    const bool keep = continue_story && story_generation_ == generation && story_tokens_ > 0;
    int base = 0;  // story tokens kept after the prefix
    std::vector<llama_token> segment;
    if (!keep) {
        story_sentences_.clear();
        segment = tokenize(vocab, " " + sentence, false, false);
    } else {
        segment = tokenize(vocab, "\nSentence: " + sentence, false, false);
        base = story_tokens_;
        if (story_sentences_.size() >= kMaxStorySentences ||
            base + static_cast<int>(segment.size()) > kMaxStoryTokens) {
            // Window full: start it again from the last few sentences (one larger decode), dropping
            // the oldest until the rebuilt window fits the token budget again.
            const size_t drop = story_sentences_.size() - std::min(story_sentences_.size(), kReseedSentences);
            story_sentences_.erase(story_sentences_.begin(), story_sentences_.begin() + static_cast<long>(drop));
            base = 0;
            while (true) {
                std::string text;
                for (const std::string &s : story_sentences_) text += (text.empty() ? " " : "\nSentence: ") + s;
                text += (text.empty() ? " " : "\nSentence: ") + sentence;
                segment = tokenize(vocab, text, false, false);
                if (static_cast<int>(segment.size()) <= kMaxStoryTokens || story_sentences_.empty()) break;
                story_sentences_.erase(story_sentences_.begin());
            }
        }
    }
    if (!llama_memory_seq_rm(llama_get_memory(ctx_), 0, prefix_tokens_ + base, -1)) {
        reset_story_locked();
        r.error = "cannot reset request tokens";
        running_generation_ = INT64_MAX;
        return r;
    }
    const bool with_context = keep && !story_sentences_.empty();  // earlier story sentences in the window
    std::vector<llama_token> tokens = segment;
    const std::vector<llama_token> suffix =
        tokenize(vocab, (with_context ? std::string(kStoryHint) : std::string()) + prompt_suffix_, false, true);
    tokens.insert(tokens.end(), suffix.begin(), suffix.end());
    r.prompt_tokens = static_cast<int>(tokens.size());
    // One llama_decode call must fit the batch, and the request must fit the context with room
    // for the classification steps. The window budget keeps this true; refuse rather than abort.
    const int n_ctx = static_cast<int>(llama_n_ctx(ctx_));
    if (r.prompt_tokens > static_cast<int>(llama_n_batch(ctx_)) ||
        prefix_tokens_ + base + r.prompt_tokens + kMaxValueTokens > n_ctx) {
        reset_story_locked();
        running_generation_ = INT64_MAX;
        r.error = "request too long (" + std::to_string(r.prompt_tokens) + " tokens)";
        return r;
    }

    deadline_ms_ = start + timeout_ms;
    const bool prompt_ok = decode(ctx_, tokens);
    r.prompt_ms = now_ms() - start;

    // Stop between phases too, not only inside llama_decode.
    auto stopped = [&] { return cancelled() || now_ms() > deadline_ms_.load(); };
    if (prompt_ok) {
        const float *logits = llama_get_logits_ith(ctx_, -1);
        float best = -INFINITY, best_none = -INFINITY;
        for (size_t i = 0; i < first_tokens_.size(); ++i) {
            const float l = logits[first_tokens_[i]];
            best = std::max(best, l);
            if (first_leads_to_none_[i]) best_none = std::max(best_none, l);
        }
        r.none_gap = std::isfinite(best_none) ? best - best_none : 99.0f;
    }
    std::string expression;
    const bool ok = prompt_ok && !stopped() && classify(ctx_, vocab, expression, r.sampled_tokens);
    const bool was_cancelled = cancelled();
    const bool timed_out = now_ms() > deadline_ms_.load();
    deadline_ms_ = 0;
    running_generation_ = INT64_MAX;
    r.total_ms = now_ms() - start;
    r.generate_ms = r.total_ms - r.prompt_ms;
    // A late or cancelled result is never applied, even if generation completed.
    if (!ok || was_cancelled || timed_out) {
        reset_story_locked();  // the cache may hold a half-decoded request
        r.error = was_cancelled ? "cancelled" : timed_out ? "timeout after " + std::to_string(timeout_ms) + " ms"
                                                       : "inference failed";
        return r;
    }
    // Keep this sentence as story context; drop the hint, template suffix and classification steps.
    const int kept = base + static_cast<int>(segment.size());
    if (!llama_memory_seq_rm(llama_get_memory(ctx_), 0, prefix_tokens_ + kept, -1)) {
        reset_story_locked();
    } else {
        story_tokens_ = kept;
        story_generation_ = generation;
        story_sentences_.push_back(sentence);
    }
    r.ok = true;
    r.json = std::string(kJsonOpen) + expression + kJsonClose;  // look is decided by the app's rules
    return r;
}

}  // namespace chati

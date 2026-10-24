#pragma once

#include <atomic>
#include <cstdint>
#include <mutex>
#include <string>
#include <vector>

struct llama_model;
struct llama_context;

namespace chati {

/// Result of one sentence → intent inference.
struct BrainResult {
    bool ok = false;
    std::string json;   // {"expression":"…"} on success
    std::string error;  // reason on failure
    int prompt_tokens = 0;
    int sampled_tokens = 0;
    /// Logit of the best allowed first token minus the best first token on the path to "NONE"
    /// (0 when the model's top choice already leads to NONE). Small = close to "no reaction".
    float none_gap = 0.0f;
    int64_t prompt_ms = 0;
    int64_t generate_ms = 0;
    int64_t total_ms = 0;
};

/// The on-phone eye model (Gemma 3 1B via llama.cpp). It reads one English sentence and picks an
/// expression; it never draws and never decides timing (the EyeController does) or gaze (the
/// app's look rules do).
///
/// Speed: the system prompt is decoded once and kept in the KV cache; each sentence only removes
/// the previous request's tokens and decodes its own. The expression is classified from the
/// logits under a grammar and stops as soon as one label is left, usually with no decode beyond
/// the sentence itself. Output: {"expression":"…"}.
///
/// Not called from the render thread; calls are serialized by an internal mutex.
class EyeBrain {
public:
    static EyeBrain &instance();

    /// Loads the GGUF model and decodes the system prompt. Returns false with `error` set.
    bool load(const std::string &model_path, int threads, std::string &error);
    bool loaded();
    void release();

    /// Changes generation / prompt-processing thread counts of the loaded model (no reload).
    void set_threads(int threads, int threads_batch);

    /// Prompt bundle (E0.7): the system prompt and the labels the model chooses from (the grammar is
    /// built from them; NONE and NEUTRAL are required). If the model is loaded, its context is
    /// rebuilt with the new prompt (the model file stays loaded); on failure the previous bundle is
    /// restored and false returned with `error`. Callers stop any running request first.
    bool set_prompt(const std::string &system_prompt, const std::vector<std::string> &labels, std::string &error);

    /// `generation` identifies the request; it is cancelled if cancel_older_than() later passes a
    /// larger generation. With `continue_story`, earlier sentences inferred with the same generation
    /// (one story) stay in the KV cache as context, within a bounded window; otherwise the context
    /// starts empty.
    BrainResult infer(const std::string &sentence, int timeout_ms, int64_t generation, bool continue_story);

    /// Stops every request whose generation is < `generation` at its next step (thread-safe).
    /// Request-specific: the request for `generation` itself is never affected.
    void cancel_older_than(int64_t generation) {
        int64_t current = cancel_below_.load();
        while (current < generation && !cancel_below_.compare_exchange_weak(current, generation)) {}
    }

private:
    EyeBrain();
    static bool abort_check(void *self);
    void release_locked();
    /// A context built for a prompt, not yet in use (see build_context_locked / commit_context_locked).
    struct BuiltContext {
        llama_context *ctx = nullptr;
        int prefix_tokens = 0;
        std::string prompt_suffix;
        std::vector<int32_t> first_tokens;
        std::vector<bool> first_leads_to_none;
    };
    bool build_context_locked(const std::string &prompt, const std::string &grammar, size_t n_labels,
                              BuiltContext &out, std::string &error);
    void commit_context_locked(BuiltContext &&built);
    void reset_story_state_locked();
    void reset_story_locked();

    std::mutex mutex_;
    llama_model *model_ = nullptr;
    llama_context *ctx_ = nullptr;
    int prefix_tokens_ = 0;
    std::string prompt_suffix_;  // template text after the sentence (special tokens allowed)
    std::string system_prompt_;           // current prompt bundle
    std::vector<std::string> labels_;
    std::string grammar_;
    int threads_ = 4;
    int threads_batch_ = 4;
    int story_tokens_ = 0;               // KV tokens after the prefix holding the current story
    int64_t story_generation_ = -1;      // generation (story) those tokens belong to
    std::vector<std::string> story_sentences_;  // their sentences, oldest first (for re-seeding)
    std::vector<int32_t> first_tokens_;  // tokens the expression grammar allows first
    std::vector<bool> first_leads_to_none_;
    std::atomic<int64_t> deadline_ms_{0};  // read by abort_check (may run on ggml worker threads)
    std::atomic<int64_t> cancel_below_{0};
    std::atomic<int64_t> running_generation_{INT64_MAX};  // INT64_MAX = not cancellable (load)
    bool cancelled() const { return running_generation_.load() < cancel_below_.load(); }
};

}  // namespace chati

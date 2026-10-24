#include <jni.h>

#include <android/log.h>
#include <android/native_window_jni.h>
#include <android/ndk-version.h>

#include <atomic>
#include <string>

#include "eye_brain.hpp"
#include "eye_renderer.hpp"

namespace {

std::string version_string() {
    return std::string("chati_eyes 0.9.4 | C++ ") + std::to_string(__cplusplus) +
           " | NDK r" + std::to_string(__NDK_MAJOR__) + "." + std::to_string(__NDK_MINOR__) +
           " | clang " + __clang_version__ + " | expressive_eyes v1.3.9 + Monochrome Blue";
}

std::atomic<bool> logged{false};

chati::EyeRenderer &renderer() { return chati::EyeRenderer::instance(); }

}  // namespace

extern "C" {

JNIEXPORT jstring JNICALL
Java_app_mychatibot_NativeBridge_nativeVersion(JNIEnv *env, jobject /* this */) {
    const std::string version = version_string();
    if (!logged.exchange(true)) {
        __android_log_print(ANDROID_LOG_INFO, "ChatiEyes", "native: %s", version.c_str());
    }
    return env->NewStringUTF(version.c_str());
}

JNIEXPORT void JNICALL
Java_app_mychatibot_NativeBridge_nativeStart(JNIEnv *, jobject) {
    renderer().start();
}

JNIEXPORT void JNICALL
Java_app_mychatibot_NativeBridge_nativeAttachSurface(JNIEnv *env, jobject, jobject surface, jint owner) {
    if (surface == nullptr) return;
    ANativeWindow *window = ANativeWindow_fromSurface(env, surface);  // +1 reference
    if (window == nullptr) return;
    renderer().attach_window(window, owner);                          // renderer holds its own
    ANativeWindow_release(window);
}

JNIEXPORT void JNICALL
Java_app_mychatibot_NativeBridge_nativeDetachSurface(JNIEnv *, jobject, jint owner) {
    renderer().detach_window(owner);
}

JNIEXPORT void JNICALL
Java_app_mychatibot_NativeBridge_nativeSetExpression(JNIEnv *, jobject, jint id) {
    renderer().post(chati::SetExpression{id});
}

JNIEXPORT void JNICALL
Java_app_mychatibot_NativeBridge_nativeLookAt(JNIEnv *, jobject, jfloat x, jfloat y) {
    renderer().post(chati::LookAt{x, y});
}

JNIEXPORT void JNICALL
Java_app_mychatibot_NativeBridge_nativeBlink(JNIEnv *, jobject) {
    renderer().post(chati::Blink{});
}

JNIEXPORT void JNICALL
Java_app_mychatibot_NativeBridge_nativeSetShape(JNIEnv *, jobject, jint id) {
    renderer().post(chati::SetShape{id});
}

JNIEXPORT void JNICALL
Java_app_mychatibot_NativeBridge_nativeSetEffects(JNIEnv *, jobject, jint mask) {
    renderer().post(chati::SetEffects{static_cast<uint32_t>(mask)});
}

JNIEXPORT void JNICALL
Java_app_mychatibot_NativeBridge_nativeSetColor(JNIEnv *, jobject, jint rgb, jint fade_ms) {
    renderer().post(chati::SetColor{static_cast<uint32_t>(rgb) & 0xffffffu, fade_ms});
}

JNIEXPORT jstring JNICALL
Java_app_mychatibot_NativeBridge_nativeStats(JNIEnv *env, jobject) {
    return env->NewStringUTF(renderer().stats().c_str());
}

// --- Eye model (Stage 6) ---

/// Returns null on success, otherwise the error message.
JNIEXPORT jstring JNICALL
Java_app_mychatibot_NativeBridge_nativeBrainLoad(JNIEnv *env, jobject, jstring path, jint threads) {
    const char *p = env->GetStringUTFChars(path, nullptr);
    std::string error;
    const bool ok = chati::EyeBrain::instance().load(p, threads, error);
    env->ReleaseStringUTFChars(path, p);
    return ok ? nullptr : env->NewStringUTF(error.c_str());
}

JNIEXPORT void JNICALL
Java_app_mychatibot_NativeBridge_nativeBrainRelease(JNIEnv *, jobject) {
    chati::EyeBrain::instance().release();
}

/// Returns null on success, otherwise the error. `prompt` is UTF-8 bytes (Kotlin side), so the
/// text reaches llama.cpp exactly; `labels` is comma-separated ASCII, validated natively.
JNIEXPORT jstring JNICALL
Java_app_mychatibot_NativeBridge_nativeBrainSetPrompt(JNIEnv *env, jobject, jbyteArray prompt, jstring labels) {
    if (prompt == nullptr || labels == nullptr) return env->NewStringUTF("missing prompt or labels");
    const jsize size = env->GetArrayLength(prompt);
    std::string prompt_text(static_cast<size_t>(size), '\0');
    if (size > 0) env->GetByteArrayRegion(prompt, 0, size, reinterpret_cast<jbyte *>(prompt_text.data()));
    const char *l = env->GetStringUTFChars(labels, nullptr);
    if (l == nullptr) return env->NewStringUTF("cannot read labels");
    std::vector<std::string> label_list;
    std::string current;
    bool empty_field = false;
    for (const char *c = l;; ++c) {
        if (*c == ',' || *c == '\0') {
            if (current.empty()) empty_field = true;
            label_list.push_back(current);
            current.clear();
            if (*c == '\0') break;
        } else {
            current += *c;
        }
    }
    env->ReleaseStringUTFChars(labels, l);
    if (empty_field) return env->NewStringUTF("empty label");
    std::string error;
    const bool ok = chati::EyeBrain::instance().set_prompt(prompt_text, label_list, error);
    return ok ? nullptr : env->NewStringUTF(error.c_str());
}

JNIEXPORT void JNICALL
Java_app_mychatibot_NativeBridge_nativeBrainSetThreads(JNIEnv *, jobject, jint threads, jint threads_batch) {
    chati::EyeBrain::instance().set_threads(threads, threads_batch);
}

/// Thread-safe: requests with a generation below `generation` stop at their next step.
JNIEXPORT void JNICALL
Java_app_mychatibot_NativeBridge_nativeBrainCancelOlderThan(JNIEnv *, jobject, jlong generation) {
    chati::EyeBrain::instance().cancel_older_than(generation);
}

/// Tab-separated: "OK|ERR", json-or-error, prompt_tokens, sampled_tokens, prompt_ms, generate_ms, total_ms, none_gap.
JNIEXPORT jstring JNICALL
Java_app_mychatibot_NativeBridge_nativeBrainInfer(JNIEnv *env, jobject, jstring sentence, jint timeout_ms,
                                                 jlong generation, jboolean continue_story) {
    const char *s = env->GetStringUTFChars(sentence, nullptr);
    const chati::BrainResult r =
        chati::EyeBrain::instance().infer(s, timeout_ms, generation, continue_story == JNI_TRUE);
    env->ReleaseStringUTFChars(sentence, s);
    const std::string out = std::string(r.ok ? "OK" : "ERR") + "\t" + (r.ok ? r.json : r.error) + "\t" +
                            std::to_string(r.prompt_tokens) + "\t" + std::to_string(r.sampled_tokens) + "\t" +
                            std::to_string(r.prompt_ms) + "\t" + std::to_string(r.generate_ms) + "\t" +
                            std::to_string(r.total_ms) + "\t" + std::to_string(r.none_gap);
    return env->NewStringUTF(out.c_str());
}

}  // extern "C"

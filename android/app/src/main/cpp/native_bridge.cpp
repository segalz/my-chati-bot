#include <jni.h>

#include <android/log.h>
#include <android/native_window_jni.h>
#include <android/ndk-version.h>

#include <atomic>
#include <string>

#include "eye_renderer.hpp"

namespace {

std::string version_string() {
    return std::string("chati_eyes 0.2.1 | C++ ") + std::to_string(__cplusplus) +
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
Java_app_mychatibot_NativeBridge_nativeSetSurface(JNIEnv *env, jobject, jobject surface) {
    if (surface == nullptr) {
        renderer().set_window(nullptr);
        return;
    }
    ANativeWindow *window = ANativeWindow_fromSurface(env, surface);  // +1 reference
    renderer().set_window(window);                                    // renderer holds its own
    if (window != nullptr) ANativeWindow_release(window);
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

JNIEXPORT jstring JNICALL
Java_app_mychatibot_NativeBridge_nativeStats(JNIEnv *env, jobject) {
    return env->NewStringUTF(renderer().stats().c_str());
}

}  // extern "C"

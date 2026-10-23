# My Chati Bot — Android app

Kotlin shell plus a native C++ library (`libchati_eyes`). Stage 3 is a skeleton: a fullscreen black
screen with a debug overlay that shows the app and native-library versions. The original
`espp/expressive_eyes` v1.3.9 engine and Monochrome Blue renderer arrive in Stage 4
(see [plan/eyes/plan.md](../plan/eyes/plan.md)).

Toolchain (pinned): Gradle 9.8.2, AGP 9.4.1 (built-in Kotlin), compileSdk/targetSdk 36, minSdk 26,
NDK 28.2.13676358, CMake 3.31.6, JDK 17+, ABI `arm64-v8a`.

```powershell
# from android/
.\gradlew.bat :app:assembleDebug
adb -s <serial> install -r app\build\outputs\apk\debug\app-debug.apk
adb -s <serial> shell am start -W -n app.mychatibot/.MainActivity
```

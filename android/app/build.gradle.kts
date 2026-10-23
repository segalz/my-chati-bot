plugins {
    id("com.android.application")
}

android {
    namespace = "app.mychatibot"
    compileSdk = 36
    ndkVersion = "28.2.13676358"

    defaultConfig {
        applicationId = "app.mychatibot"
        minSdk = 29
        targetSdk = 36
        versionCode = 20
        versionName = "0.9.3"

        ndk {
            abiFilters += "arm64-v8a"
        }
        externalNativeBuild {
            cmake {
                // Optimized native code even in debug APKs: unoptimized llama.cpp/LVGL are several
                // times slower, which would distort every phone measurement.
                arguments += "-DCMAKE_BUILD_TYPE=Release"
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.31.6"
        }
    }

    sourceSets {
        getByName("main") {
            // Prebuilt sherpa-onnx TTS runtime (Stage 8); see third_party/sherpa-onnx-v1.13.8/SOURCE.md.
            jniLibs.directories.add("../../third_party/sherpa-onnx-v1.13.8/jniLibs")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}

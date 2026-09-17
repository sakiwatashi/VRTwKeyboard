plugins {
    id("com.android.application")
}

android {
    namespace = "tw.pinnedbopomofo.quest"
    compileSdk = 37

    defaultConfig {
        applicationId = "tw.pinnedbopomofo.quest"
        minSdk = 32
        // Horizon OS 自 v76 起是 Android 14；Meta 規定新上傳的 App 必須 target 34
        targetSdk = 34
        versionCode = 1
        versionName = "0.0.1"

        // Quest 3S 是 arm64-v8a；其他 ABI 的 native library 只會讓 APK 變大
        ndk { abiFilters += "arm64-v8a" }
    }

    packaging {
        jniLibs {
            // Kotlin API 只用得到 jni 與 onnxruntime：libsherpa-onnx-jni.so 的相依清單裡
            // 沒有 c-api／cxx-api，排除它們可省約 4.7 MB
            excludes += listOf("**/libsherpa-onnx-c-api.so", "**/libsherpa-onnx-cxx-api.so")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        // targetSdk 34 是 Meta 的要求，不是疏忽
        disable += "ExpiredTargetSdkVersion"
    }
}

dependencies {
    // sherpa-onnx 官方沒有發佈 Maven artifact，只提供 GitHub Releases 的 AAR。
    // 檔案不進 git；來源、revision 與 SHA256 見 docs/licenses/asr-paraformer.md
    implementation(files("libs/sherpa-onnx-1.13.8.aar"))

    // 簡體 → 台灣正體（含台灣用語）。純 Java、Apache-2.0，不需要 native library
    implementation("com.github.houbb:opencc4j:1.14.0")

    testImplementation("junit:junit:4.13.2")
    // Android 的 org.json 在 JVM 單元測試裡只是空殼，要換成真的實作
    testImplementation("org.json:json:20260814")
}

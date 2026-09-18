// 在 build.gradle.kts 裡 `java` 會被解析成 Gradle 的 java 擴充，不是套件名，
// 所以 java.util.Properties 寫在行內會找不到——要先 import。
import java.io.File
import java.util.Properties

plugins {
    id("com.android.application")
}

// Release 簽章從 local.properties 讀，那個檔不進版控。
// 沒設定就不做 release 簽章（別人 clone 下來仍然建得出 debug 版）。
val signing = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val releaseStore = signing.getProperty("RELEASE_STORE_FILE")?.let(::File)?.takeIf { it.exists() }

android {
    namespace = "tw.pinnedbopomofo.quest"
    compileSdk = 37

    defaultConfig {
        applicationId = "tw.pinnedbopomofo.quest"
        minSdk = 32
        // Horizon OS 自 v76 起是 Android 14；Meta 規定新上傳的 App 必須 target 34
        targetSdk = 34
        // versionName 要跟 GitHub release 的 tag 對得起來：Updater 只比數字段落，
        // 所以 tag 寫成 v0.2.0 時這裡就是 0.2.0。兩邊不同步會讓更新檢查誤判
        // （0.0.1 對上 v0.1 會被判成「有新版」，其實是同一版）。
        versionCode = 3
        versionName = "0.2.1"

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

    signingConfigs {
        if (releaseStore != null) {
            create("release") {
                storeFile = releaseStore
                storePassword = signing.getProperty("RELEASE_STORE_PASSWORD")
                keyAlias = signing.getProperty("RELEASE_KEY_ALIAS")
                keyPassword = signing.getProperty("RELEASE_KEY_PASSWORD")
                // Quest 是 Android 14，v2/v3 就夠，不需要舊的 v1（JAR 簽章）
                enableV1Signing = false
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            // 沒有 local.properties 的人（例如剛 clone 的人）仍然建得出 debug 版，
            // 只是 release 版會是未簽章的——不要為此讓整個建置失敗。
            signingConfig = signingConfigs.findByName("release")
            // 先不開 minify：sherpa-onnx 走 JNI，縮減規則要另外驗證過才敢開
            isMinifyEnabled = false
        }
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

    // 只為了 FileProvider：Android N 之後不能直接把 file:// 遞給系統安裝器。
    // 這是整個專案唯一的 androidx 相依，介面全部還是程式繪製、沒有 Compose。
    implementation("androidx.core:core:1.13.1")

    testImplementation("junit:junit:4.13.2")
    // Android 的 org.json 在 JVM 單元測試裡只是空殼，要換成真的實作
    testImplementation("org.json:json:20260814")
}

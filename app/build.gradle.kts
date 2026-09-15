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

androidComponents {
    onVariants { variant ->
        // 詞庫直接用 pime-bopomofo-core 的資料，不另外複製一份以免兩邊漂移
        variant.sources.assets?.addStaticSourceDirectory(
            rootDir.resolve("../pime-bopomofo-core/bopomofo_core/data").canonicalPath
        )
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    // Android 的 org.json 在 JVM 單元測試裡只是空殼，要換成真的實作
    testImplementation("org.json:json:20260814")
}

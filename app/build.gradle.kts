import java.util.Properties

plugins {
    id("com.android.application")
}

// 签名信息不写死在仓库里：优先读 keystore/signing.properties（已被 .gitignore 排除），
// 其次读环境变量；两者都没有时 release 退回 debug 签名，保证 clone 下来能直接编译。
val signingProps = Properties().apply {
    val f = rootProject.file("keystore/signing.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val releaseStore = signingProps.getProperty("storeFile") ?: System.getenv("SIGN_STORE_FILE")
val hasReleaseKey = releaseStore != null && file(releaseStore).exists()

android {
    namespace = "com.dsh.toolbox"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.dsh.toolbox"
        minSdk = 24
        targetSdk = 34
        versionCode = 15
        versionName = "2.3"
    }

    signingConfigs {
        create("release") {
            if (hasReleaseKey) {
                storeFile = file(releaseStore!!)
                storePassword = signingProps.getProperty("storePassword") ?: System.getenv("SIGN_STORE_PASSWORD")
                keyAlias = signingProps.getProperty("keyAlias") ?: System.getenv("SIGN_KEY_ALIAS")
                keyPassword = signingProps.getProperty("keyPassword") ?: System.getenv("SIGN_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (hasReleaseKey) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        create("diag") {
            initWith(getByName("release"))
            isDebuggable = true
            matchingFallbacks += listOf("release")
            applicationIdSuffix = ""
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("androidx.work:work-runtime:2.9.0")
}

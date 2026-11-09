plugins {
    id("com.android.application")
}

android {
    namespace = "io.github.dyvolguard"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.dyvolguard"
        minSdk = 28
        targetSdk = 35
        versionCode = 3
        versionName = "2.1"
    }

    signingConfigs {
        create("fixed") {
            storeFile = file("dyvolguard.jks")
            storePassword = "dyvolguard"
            keyAlias = "dyvolguard"
            keyPassword = "dyvolguard"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("fixed")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // Hook 端：框架提供，只编译不打包
    compileOnly("io.github.libxposed:api:102.0.0")
    // 界面端：和 LSPosed 通信（激活状态 + 远程配置），要打包进 APK
    implementation("io.github.libxposed:service:102.0.0")
}

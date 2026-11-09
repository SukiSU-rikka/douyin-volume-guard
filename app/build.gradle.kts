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
        versionCode = 1
        versionName = "1.0"
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
    // 经典 Xposed API，LSPosed 完整兼容；只编译不打包
    compileOnly("de.robv.android.xposed:api:82")
}

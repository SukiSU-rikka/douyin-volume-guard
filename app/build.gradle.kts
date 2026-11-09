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
        versionCode = 2
        versionName = "2.0"
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
    // libxposed 现代 API（LSPosed 2.2.1 = API 102），只编译不打包
    compileOnly("io.github.libxposed:api:102.0.0")
}

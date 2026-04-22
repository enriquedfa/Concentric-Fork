plugins {
    id("com.android.application")
}

android {
    namespace = "com.watchfacedesigns.Concentric"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.watchfacedesigns.Concentric"
        minSdk = 33
        targetSdk = 34
        versionCode = 10000022
        versionName = "1.2.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

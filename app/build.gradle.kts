import com.android.build.api.dsl.ApplicationExtension

plugins {
    alias(libs.plugins.android.application)
}

configure<ApplicationExtension> {
    namespace = "com.dfamaya.concentric"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.dfamaya.concentric"
        // The manifest declares Watch Face Format version 4, which requires the
        // Wear OS 6 runtime (API 36) — keep minSdk in lockstep with that property.
        minSdk = 36
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
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

    lint {
        // WFF references drawables, fonts, and strings by bare name from
        // res/raw/watchface.xml, which lint cannot trace — every UnusedResources
        // hit in this project is a false positive. Audit unused assets by hand
        // (grep the resource name in res/raw and res/xml) instead.
        disable += "UnusedResources"
    }
}

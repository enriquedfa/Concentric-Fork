plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

android {
    // R-class / source package for this module. Kept distinct from applicationId
    // so it never collides with the :watchface module's R class.
    namespace = "com.dfamaya.concentric.companion"
    compileSdk = 37

    // Pin an NDK so the release build can extract native debug symbols (see the
    // `ndk { debugSymbolLevel }` block in buildTypes.release). This module ships
    // no C/C++ of its own, but dependencies (play-services, etc.) bundle prebuilt
    // .so files, and AGP needs the NDK's objcopy to strip symbols out of them.
    // Without this, Play Console warns that the App Bundle contains native code
    // with no debug symbols. This exact version is the one pre-installed on the
    // GitHub-hosted CI runner, so nothing is downloaded there; AGP fetches it on
    // demand for local release builds.
    ndkVersion = "27.3.13750724"

    defaultConfig {
        // Shares the watch face's applicationId so Google Play treats the two
        // APKs as one multi-form-factor listing. The watch APK requires
        // android.hardware.type.watch; this phone APK does not, so Play
        // delivers each to the right device and offers "install on watch".
        // Keep this in lockstep with :app's applicationId.
        applicationId = "com.dfamaya.concentric"
        // Wide phone reach is the whole point of the companion — keep the floor
        // low. minSdk 26 is required for the adaptive launcher icon.
        minSdk = 26
        targetSdk = 36
        // Within a shared listing each APK needs a UNIQUE versionCode. The watch
        // APK versions from :app; the phone APK uses its own env var so the two
        // ranges stay disjoint (phone codes start at 100000).
        versionCode = System.getenv("CONCENTRIC_MOBILE_VERSION_CODE")?.toIntOrNull() ?: 100001
        versionName = System.getenv("CONCENTRIC_VERSION_NAME") ?: "1.0.0"
    }

    // Multi-form-factor delivery requires the phone and watch APKs to be signed
    // with the SAME key, so this reuses the watch face's signing env vars. CI
    // populates them from GitHub Secrets; local release builds export the same.
    val releaseStoreFile = System.getenv("CONCENTRIC_KEYSTORE_PATH")
    val releaseStorePassword = System.getenv("CONCENTRIC_KEYSTORE_PASSWORD")
    val releaseKeyAlias = System.getenv("CONCENTRIC_KEY_ALIAS")
    val releaseKeyPassword = System.getenv("CONCENTRIC_KEY_PASSWORD")
    val hasReleaseSigning = !releaseStoreFile.isNullOrBlank() &&
        !releaseStorePassword.isNullOrBlank() &&
        !releaseKeyAlias.isNullOrBlank() &&
        !releaseKeyPassword.isNullOrBlank()

    if (hasReleaseSigning) {
        signingConfigs {
            create("release") {
                storeFile = file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Package native debug symbols for the prebuilt .so pulled in by our
            // dependencies into the AAB so Play can symbolicate native crashes and
            // ANRs — fixes the Play Console "this App Bundle contains native code,
            // and you've not uploaded debug symbols" warning. Symbols are stripped
            // from what's delivered to devices, so there's no user download cost.
            // Needs `ndkVersion` (set in the android block) for objcopy.
            ndk {
                debugSymbolLevel = "FULL"
            }
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.wear.remote.interactions)
    implementation(libs.play.services.wearable)
    implementation(libs.kotlinx.coroutines.play.services)

    debugImplementation(libs.androidx.compose.ui.tooling)
}

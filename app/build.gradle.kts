plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Single place to change the ONNX Runtime version.
val ortVersion = "1.27.0"

android {
    namespace = "com.featherx.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.featherx.app"
        minSdk = 28          // ImageDecoder needs API 28
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0-dev"

        // Keep the APK small: only ARM ABIs (virtually all phones).
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true   // BuildConfig.VERSION_NAME is shown on Home/Settings
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.08.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.10.1")

    implementation("com.microsoft.onnxruntime:onnxruntime-android:$ortVersion")

    testImplementation("junit:junit:4.13.2")
}

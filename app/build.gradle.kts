plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Single place to change the ONNX Runtime version.
// 1.27.0 is listed on Maven Central (Sonatype page); if Gradle cannot resolve it, use "1.26.0".
val ortVersion = "1.27.0"

android {
    namespace = "com.opensr.ai"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.opensr.ai"
        minSdk = 28          // ImageDecoder needs API 28
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

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
    implementation("androidx.activity:activity-compose:1.10.1")

    implementation("com.microsoft.onnxruntime:onnxruntime-android:$ortVersion")

    testImplementation("junit:junit:4.13.2")
}

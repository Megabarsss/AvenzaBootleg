plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "id.pdfmaps"
    compileSdk = 35
    defaultConfig {
        applicationId = "id.pdfmaps"
        minSdk = 26
        targetSdk = 35
        versionCode = 8
        versionName = "0.7"
    }
    signingConfigs {
        // Kunci tetap supaya tiap versi bisa dipasang di atas versi sebelumnya tanpa uninstall
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }
    buildTypes {
        // APK debug dibuat tidak "debuggable" agar Compose berjalan jauh lebih cepat dan hemat
        debug { isDebuggable = false }
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
}

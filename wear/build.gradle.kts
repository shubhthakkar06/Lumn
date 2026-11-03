plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.example.musicapp.wear"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.musicapp.wear"
        minSdk = 30
        targetSdk = 35
        versionCode = 36
        versionName = "2.7.9"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Wear OS Compose (Galaxy Watch 7 Material 3)
    implementation("androidx.wear.compose:compose-material3:1.0.0-alpha30")
    implementation("androidx.wear.compose:compose-foundation:1.4.1")
    implementation("androidx.wear.compose:compose-navigation:1.4.1")
    implementation("androidx.wear:wear:1.3.0")
    implementation("androidx.wear:wear-tooling-preview:1.0.0")

    // Wearable Data Layer (Phone sync)
    implementation("com.google.android.gms:play-services-wearable:19.0.0")

    // Wear Tiles & Complications
    implementation("androidx.wear.tiles:tiles:1.4.1")
    implementation("androidx.wear.tiles:tiles-material:1.4.1")
    implementation("androidx.wear.watchface:watchface-complications-data-source:1.2.1")

    // Core & Lifecycle
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Media3
    implementation("androidx.media3:media3-exoplayer:1.6.0")
    implementation("androidx.media3:media3-session:1.6.0")

    // Icons
    implementation("androidx.compose.material:material-icons-extended")

    // Serialization & Network
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Image loading with Coil
    implementation("io.coil-kt.coil3:coil-compose:3.1.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.1.0")
}

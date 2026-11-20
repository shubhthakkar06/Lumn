// Platform-neutral music code shared by the Android app and the desktop app:
// song model, providers (JioSaavn, YouTube, Audius, Jamendo), provider routing,
// recording matching, stream quality and the JioSaavn/Deezer catalog clients.
plugins {
    kotlin("jvm")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
    api("com.squareup.okhttp3:okhttp:4.12.0")
    api("com.github.TeamNewPipe:NewPipeExtractor:v0.26.5")

    testImplementation(libs.junit)
}

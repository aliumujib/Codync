plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

// Full-screen viewer for files a bot shares: video and audio streamed from the computer, images, PDFs,
// text and ZIPs. Knows nothing about the host protocol; the app hands it a `ViewerFile`.
android {
    namespace = "com.codync.android.viewer"
    compileSdk = 37
    defaultConfig.minSdk = 29
    buildFeatures.compose = true
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
kotlin.compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)

dependencies {
    implementation(project(":design"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.compose)
    implementation(libs.coroutines.android)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui.compose)
    implementation(libs.coil.compose)
    implementation(libs.telephoto.coil3)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
}

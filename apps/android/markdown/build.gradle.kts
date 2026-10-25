plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

// Markdown parser and renderer ported from Control (controlAndroid core:model/markdown + core:markdown-renderer),
// drawn with Codync's design system: tables, fenced code with copy, task lists, footnotes, file and link chips.
android {
    namespace = "com.codync.android.markdown"
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
    implementation(libs.coroutines.core)
    testImplementation(libs.junit)
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:${libs.versions.kotlin.get()}")
}

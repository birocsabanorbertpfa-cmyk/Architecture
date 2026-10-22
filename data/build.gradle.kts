import java.util.Properties

plugins {
    id("architecture.android.library")
    id("architecture.android.hilt")
    alias(libs.plugins.kotlin.serialization)
}

/**
 * Lesson 12 — an optional GitHub token, read from local.properties (gitignored).
 *
 * `BuildConfig` is generated per module, into the module's own namespace: this one is
 * `hu.csabi.architecture.data.BuildConfig`, not the app's. That is the single most common
 * surprise when splitting a project up.
 */
val githubToken: String = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}.getProperty("GITHUB_TOKEN").orEmpty()

android {
    namespace = "hu.csabi.architecture.data"

    defaultConfig {
        buildConfigField("String", "GITHUB_TOKEN", "\"$githubToken\"")
    }

    buildFeatures {
        buildConfig = true
    }
}

ksp {
    // The exported schemas moved with the database they describe.
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    api(project(":domain"))
    implementation(project(":core:common"))

    implementation(libs.retrofit)
    implementation(libs.retrofit.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.paging)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.paging.runtime)

    testImplementation(project(":core:testing"))
    testImplementation(libs.okhttp.mockwebserver)

    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.truth)
}

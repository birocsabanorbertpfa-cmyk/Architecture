plugins {
    id("architecture.android.application")
    id("architecture.android.compose")
    id("architecture.android.hilt")
}

/**
 * Lesson 12 — the app module is now almost empty, and that is the point.
 *
 * Compare this with the ~130 line build file it replaced. Everything repeatable moved into
 * the convention plugins, and everything functional moved into a module that owns it. What
 * is left here is what only an application can have: an id, a version, release signing and
 * the list of modules that make up the product.
 */
android {
    namespace = "hu.csabi.architecture"

    defaultConfig {
        applicationId = "hu.csabi.architecture"
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "hu.csabi.architecture.HiltTestRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

dependencies {
    // The composition root is the only module that depends on both sides of the graph.
    implementation(project(":core:common"))
    implementation(project(":core:ui"))
    implementation(project(":domain"))
    implementation(project(":data"))
    implementation(project(":feature:search"))
    implementation(project(":feature:labs"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.compiler)
    androidTestImplementation(libs.androidx.junit)
    // The custom runner subclasses AndroidJUnitRunner, which used to arrive transitively
    // through espresso-core. Dropping espresso made the dependency explicit - a small
    // example of what a module split surfaces.
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.ui.test.junit4)
    androidTestImplementation(libs.truth)
}

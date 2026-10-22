plugins {
    id("architecture.android.feature")
}

/**
 * Lesson 12 — what a feature module's build file looks like once conventions exist.
 *
 * Four lines of plugin and a namespace. Everything else — Compose, Hilt, the shared layers,
 * lifecycle, the test fixtures — comes from `architecture.android.feature`, which is also
 * the only place that could grant this module access to `:data`. It does not.
 */
android {
    namespace = "hu.csabi.architecture.feature.search"
}

dependencies {
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.ui.test.junit4)
    testImplementation(libs.androidx.ui.test.manifest)
}

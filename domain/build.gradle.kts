plugins {
    id("architecture.jvm.library")
}

/**
 * Lesson 12 — the domain layer, deliberately Android-free.
 *
 * This is the module that proves the architecture: it holds the model, the repository
 * contracts and the use cases, and it compiles without the Android SDK anywhere near it.
 * Its tests are plain JVM tests that run in milliseconds.
 *
 * `paging-common` is the one androidx dependency, and it is a pure JVM artifact — the
 * documented compromise from lesson 09.
 */
dependencies {
    api(project(":core:common"))
    api(libs.androidx.paging.common)

    testImplementation(project(":core:testing"))
}

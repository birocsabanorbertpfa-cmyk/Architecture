plugins {
    id("architecture.jvm.library")
}

/**
 * Lesson 14 — a KSP processor is an ordinary JVM module.
 *
 * It runs *inside the compiler*, so it must not depend on Android or on the modules it
 * processes. Its only real dependency is the symbol-processing API, whose version is pinned
 * to the same `ksp` ref as the Gradle plugin — a mismatch there fails with an unhelpful
 * `NoSuchMethodError` at compile time.
 */
dependencies {
    implementation(project(":tools:lab-annotations"))
    implementation(libs.ksp.symbolProcessingApi)
}

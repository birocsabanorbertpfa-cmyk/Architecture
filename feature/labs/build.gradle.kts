plugins {
    id("architecture.android.feature")
}

android {
    namespace = "hu.csabi.architecture.feature.labs"
}

dependencies {
    /**
     * Lesson 14 — the annotation is a compile-time dependency, the processor is a KSP one.
     *
     * `ksp(...)` puts the module on the *compiler's* classpath, not the app's: nothing from
     * the processor ends up in the APK. Using `implementation` for a processor is a common
     * mistake that ships the whole thing to users.
     */
    implementation(project(":tools:lab-annotations"))
    ksp(project(":tools:lab-processor"))

    // The paging lab renders a PagingData stream, so it needs the Compose integration.
    implementation(libs.androidx.paging.compose)
    implementation(libs.androidx.paging.runtime)
}

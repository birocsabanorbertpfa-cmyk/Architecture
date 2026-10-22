plugins {
    id("architecture.jvm.library")
}

/**
 * Lesson 12 — the shared kernel, as a plain JVM module.
 *
 * Nothing in here touches Android, and now it *cannot*: there is no Android plugin, so
 * `import android.*` does not compile. The layering rule is enforced by the build.
 *
 * `api` rather than `implementation` for coroutines, because `AppDispatchers` exposes
 * `CoroutineDispatcher` in its public signature — a consumer cannot use this module without
 * also seeing that type. Getting this wrong is why `implementation` sometimes produces
 * "cannot access class" errors in a consuming module.
 */
dependencies {
    api(libs.kotlinx.coroutines.core)
    api(libs.javax.inject)

    // Plain JUnit, not :core:testing - that module depends on this one, and a dependency
    // cycle between modules is a hard error rather than a style question.
    testImplementation(libs.junit)
}

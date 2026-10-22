plugins {
    id("architecture.jvm.library")
}

/**
 * Lesson 12 — test fixtures as their own module.
 *
 * The fakes and rules live in `src/main`, not `src/test`: a module's test sources are not
 * visible to other modules, so shared fixtures have to be production sources *of a module
 * nobody ships*. `:app` never depends on this, so none of it reaches the APK.
 *
 * Everything is `api` on purpose. A consumer writes `testImplementation(project(":core:testing"))`
 * and gets JUnit, Turbine, Truth and the coroutine test dispatchers along with it, instead of
 * repeating five dependency lines in every module.
 */
dependencies {
    api(project(":core:common"))
    api(project(":domain"))

    api(libs.junit)
    api(libs.kotlinx.coroutines.test)
    api(libs.turbine)
    api(libs.truth)
    api(libs.mockk)
    api(libs.androidx.paging.common)
}

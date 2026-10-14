package hu.csabi.architecture

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import dagger.hilt.android.testing.HiltTestApplication

/**
 * Lesson 11 — instrumented tests need a different Application.
 *
 * `@HiltAndroidApp` generated the graph into `ArchitectureApplication`, which is fine in
 * production and useless in a test: nothing in it can be replaced. `HiltTestApplication`
 * builds a graph that honours `@TestInstallIn` and `@UninstallModules`, so a test can swap
 * the network for a fake.
 *
 * Wiring it up takes this runner plus the `testInstrumentationRunner` line in build.gradle.kts
 * — forget either one and every `@HiltAndroidTest` fails with a confusing cast error.
 */
class HiltTestRunner : AndroidJUnitRunner() {
    override fun newApplication(
        cl: ClassLoader?,
        className: String?,
        context: Context?,
    ): Application = super.newApplication(cl, HiltTestApplication::class.java.name, context)
}

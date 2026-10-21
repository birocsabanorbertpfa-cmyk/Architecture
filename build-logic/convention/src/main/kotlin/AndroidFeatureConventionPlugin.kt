import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.project

/**
 * Lesson 12 — one plugin that says "this module is a feature".
 *
 * A feature module is an Android library with Compose, Hilt, and dependencies on the shared
 * layers. Encoding that as a single plugin has a second effect beyond brevity: a feature
 * physically **cannot** depend on `:data` or on another feature, because this is the only
 * place feature dependencies are declared. The architecture stops relying on everyone
 * remembering the rule.
 *
 * A new feature module then needs five lines of build script.
 */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("architecture.android.library")
        pluginManager.apply("architecture.android.compose")
        pluginManager.apply("architecture.android.hilt")

        dependencies {
            add("implementation", project(":core:common"))
            add("implementation", project(":core:ui"))
            add("implementation", project(":domain"))

            add("implementation", libs.library("androidx-core-ktx"))
            add("implementation", libs.library("androidx-lifecycle-runtime-ktx"))
            add("implementation", libs.library("androidx-lifecycle-runtime-compose"))
            add("implementation", libs.library("androidx-lifecycle-viewmodel-compose"))
            add("implementation", libs.library("androidx-hilt-navigation-compose"))
            add("implementation", libs.library("kotlinx-coroutines-android"))

            add("testImplementation", project(":core:testing"))
        }
    }
}

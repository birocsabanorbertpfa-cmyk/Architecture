import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * Lesson 12 — Compose setup and the Compose dependency set, in one place.
 *
 * Note the BOM, added with `platform()`: that is what keeps the Compose artifacts at
 * mutually compatible versions. Getting it wrong in one module out of eight produces runtime
 * `NoSuchMethodError`s that look like anything except a version problem.
 */
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

        // The plugin is applied to both application and library modules, so the extension
        // has to be found by whichever Android plugin is present. Both implement
        // CommonExtension, which is where buildFeatures lives.
        val android: CommonExtension<*, *, *, *, *, *> =
            extensions.findByType(ApplicationExtension::class.java)
                ?: extensions.findByType(LibraryExtension::class.java)
                ?: error("architecture.android.compose requires an Android plugin to be applied first")

        android.buildFeatures.compose = true

        dependencies {
            add("implementation", platform(libs.library("androidx-compose-bom")))
            add("testImplementation", platform(libs.library("androidx-compose-bom")))
            add("androidTestImplementation", platform(libs.library("androidx-compose-bom")))

            add("implementation", libs.library("androidx-ui"))
            add("implementation", libs.library("androidx-ui-graphics"))
            add("implementation", libs.library("androidx-ui-tooling-preview"))
            add("implementation", libs.library("androidx-material3"))
            add("debugImplementation", libs.library("androidx-ui-tooling"))
            add("debugImplementation", libs.library("androidx-ui-test-manifest"))
        }
    }
}

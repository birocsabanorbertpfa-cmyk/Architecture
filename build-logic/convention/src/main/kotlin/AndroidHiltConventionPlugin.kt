import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * Lesson 12 — Hilt in a module, without repeating the KSP wiring.
 *
 * Every module that declares a binding needs the annotation processor, not just the app.
 * Forgetting `ksp(hilt-compiler)` in one module is a particularly unhelpful failure: the
 * module compiles, and the missing binding only shows up as a Dagger error in `:app`,
 * pointing at a generated file nobody wrote.
 */
class AndroidHiltConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.google.devtools.ksp")
        pluginManager.apply("com.google.dagger.hilt.android")

        dependencies {
            add("implementation", libs.library("hilt-android"))
            add("ksp", libs.library("hilt-compiler"))
        }
    }
}

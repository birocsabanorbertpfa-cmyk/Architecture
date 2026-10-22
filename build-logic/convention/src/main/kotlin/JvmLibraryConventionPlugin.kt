import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.configure

/**
 * Lesson 12 — a plain JVM module, for code that has no business knowing about Android.
 *
 * The domain layer compiles with this plugin, and that is not a stylistic choice: a JVM
 * module **cannot** import `android.*` or Compose, so the layering rule from lesson 05 stops
 * being a convention people remember and becomes a compiler error. It also builds faster,
 * because none of AGP's variant machinery runs.
 */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.jvm")

        extensions.configure<JavaPluginExtension> {
            toolchain.languageVersion.set(JavaLanguageVersion.of(17))
        }

        configureTestTasks()
    }
}

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

/**
 * Lesson 12 — the settings every Android module shares, written once.
 *
 * Before this existed, `compileSdk`, `minSdk` and the JVM target were copy-pasted into every
 * module's build file. The practical failure mode of copy-paste is not ugliness: it is one
 * module drifting to a different `minSdk` and the mismatch only surfacing at runtime on an
 * old device.
 */
internal fun Project.configureKotlinAndroid(commonExtension: CommonExtension<*, *, *, *, *, *>) {
    commonExtension.apply {
        compileSdk = COMPILE_SDK

        defaultConfig {
            minSdk = MIN_SDK
        }

        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }
    }

    extensions.configure(KotlinAndroidProjectExtension::class.java) {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)

            // Warnings that are easy to ignore locally and expensive in review. Not
            // `allWarningsAsErrors` yet: that belongs with the CI setup in lesson 15, where
            // a failing build is visible to everyone rather than annoying to one person.
            freeCompilerArgs.addAll(
                "-Xconsistent-data-class-copy-visibility",
            )
        }
    }
}

internal const val COMPILE_SDK = 36
internal const val MIN_SDK = 26
internal const val TARGET_SDK = 36

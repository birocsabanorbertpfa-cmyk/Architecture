import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.withType
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

        // Robolectric cannot inflate anything without the merged resources and manifest.
        testOptions.unitTests.isIncludeAndroidResources = true

        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }
    }

    configureTestTasks()

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

/**
 * Lesson 12 — the test memory budget, in one place instead of per module.
 *
 * Gradle forks one test JVM per CPU by default. With eight modules on a 16 GB machine that
 * is a reliable way to start swapping, and a Robolectric fork loads a whole Android runtime
 * into each one. Capping it here means a new module inherits the budget rather than
 * rediscovering the problem.
 */
internal fun Project.configureTestTasks() {
    tasks.withType<Test>().configureEach {
        maxHeapSize = "1g"
        maxParallelForks = 1
        jvmArgs("-XX:MaxMetaspaceSize=512m")
    }
}

internal const val COMPILE_SDK = 36
internal const val MIN_SDK = 26
internal const val TARGET_SDK = 36

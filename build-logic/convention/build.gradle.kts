plugins {
    `kotlin-dsl`
}

group = "hu.csabi.architecture.buildlogic"

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

/**
 * `compileOnly`, not `implementation`: these plugins are already on the classpath of the
 * build that applies the conventions. Depending on them properly would put two copies of
 * AGP on the classpath and fail with a confusing linkage error.
 */
dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
}

/**
 * Registering the plugins by id is what lets a module write `alias(libs.plugins...)` or
 * `id("architecture.android.library")` instead of repeating forty lines of configuration.
 */
gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "architecture.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "architecture.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidCompose") {
            id = "architecture.android.compose"
            implementationClass = "AndroidComposeConventionPlugin"
        }
        register("androidHilt") {
            id = "architecture.android.hilt"
            implementationClass = "AndroidHiltConventionPlugin"
        }
        register("androidFeature") {
            id = "architecture.android.feature"
            implementationClass = "AndroidFeatureConventionPlugin"
        }
        register("jvmLibrary") {
            id = "architecture.jvm.library"
            implementationClass = "JvmLibraryConventionPlugin"
        }
    }
}

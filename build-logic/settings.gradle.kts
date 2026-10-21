/**
 * Lesson 12 — build-logic is an *included build*, not buildSrc.
 *
 * The difference matters on a real project: any change inside `buildSrc` invalidates the
 * build script classpath of every module, so editing one convention plugin recompiles
 * everything. An included build is a separate build with its own cache, so a change here
 * rebuilds only what depends on it.
 */
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }

    // The same version catalog the app uses, so plugin versions cannot drift from library
    // versions. There is exactly one place where a version is written down.
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "build-logic"
include(":convention")

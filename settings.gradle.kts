pluginManagement {
    /**
     * Lesson 12 — the convention plugins come from an included build.
     *
     * `includeBuild` puts build-logic's plugins on this build's plugin classpath, so a module
     * can write `id("architecture.android.feature")`. Unlike buildSrc, editing one plugin
     * here does not invalidate every module's build script.
     */
    includeBuild("build-logic")

    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Architecture"

/**
 * Lesson 12 — the module graph, and the dependency direction it enforces:
 *
 *   :app ──> :feature:*  ──┐
 *     │                     ├──> :domain ──> :core:common
 *     └────> :data ─────────┘
 *
 * `:data` and `:feature:*` both depend on `:domain`, and neither can see the other. The app
 * is the only module that knows both exist - it is the composition root, nothing more.
 */
include(":app")
include(":core:common")
include(":core:ui")
include(":core:testing")
include(":domain")
include(":data")
include(":feature:search")
include(":feature:labs")

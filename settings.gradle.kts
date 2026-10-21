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
include(":app")

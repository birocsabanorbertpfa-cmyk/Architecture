import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

/**
 * Lesson 12 — convention plugins read the same version catalog the modules do.
 *
 * A plugin cannot use the generated `libs.` accessors (those are produced for build scripts,
 * not for precompiled plugin classes), so it looks the catalog up by name instead. The point
 * is unchanged: a version is written down in exactly one file.
 */
internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.library(alias: String) = findLibrary(alias).get()

internal fun VersionCatalog.version(alias: String) = findVersion(alias).get().requiredVersion

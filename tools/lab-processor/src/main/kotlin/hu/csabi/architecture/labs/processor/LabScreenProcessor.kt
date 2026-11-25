package hu.csabi.architecture.labs.processor

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import java.io.OutputStreamWriter

/**
 * Lesson 14 — writing a symbol processor instead of only consuming them.
 *
 * Hilt, Room and androidx.hilt have been generating code in this project since lesson 06.
 * This is the other side of that: a processor that reads `@LabScreen` off composable
 * functions and generates the registry `MainActivity` renders its tabs from.
 *
 * Why KSP and not reflection: the list is built at **compile time**, so adding a lab is one
 * annotation and forgetting to register it is impossible. A reflective scan would cost
 * startup time, need R8 keep rules, and fail at runtime instead of at build time.
 *
 * Why KSP and not kapt: kapt generates Java stubs for every Kotlin file before the
 * annotation processors even run, which is the bulk of its cost. KSP reads Kotlin directly
 * and understands Kotlin's own concepts — nullability, default arguments, suspend — which
 * this processor actually relies on below.
 */
class LabScreenProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
) : SymbolProcessor {

    /**
     * `process` can be called more than once: KSP runs in rounds, and code generated in one
     * round can itself be annotated and processed in the next. Returning the symbols that
     * could not be handled yet is how a processor defers to a later round — here nothing is
     * deferred, so the list is empty, but writing the file only once still matters.
     */
    private var generated = false

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val annotated = resolver
            .getSymbolsWithAnnotation(ANNOTATION_FQN)
            .filterIsInstance<KSFunctionDeclaration>()
            .toList()

        if (annotated.isEmpty() || generated) return emptyList()

        val screens = annotated.mapNotNull { function -> function.toScreenOrNull() }
        if (screens.isEmpty()) return emptyList()

        writeRegistry(screens, annotated.mapNotNull { it.containingFile })
        generated = true
        return emptyList()
    }

    /**
     * Validation is half the value of a processor.
     *
     * The generated registry calls each screen with no arguments, so every parameter has to
     * have a default. KSP knows about default arguments — a Java annotation processor would
     * not — so this check is possible at all, and `logger.error` turns a would-be runtime
     * mystery into a build failure that points at the offending function.
     */
    private fun KSFunctionDeclaration.toScreenOrNull(): LabScreenEntry? {
        val annotation = annotations.firstOrNull {
            it.shortName.asString() == ANNOTATION_SIMPLE_NAME
        } ?: return null

        val title = annotation.argument("title") as? String
        val order = annotation.argument("order") as? Int
        if (title == null || order == null) {
            logger.error("@LabScreen needs both a title and an order", this)
            return null
        }

        val missingDefault = parameters.firstOrNull { !it.hasDefault }
        if (missingDefault != null) {
            logger.error(
                "@LabScreen function must be callable with no arguments, but " +
                    "'${missingDefault.name?.asString()}' has no default value",
                this,
            )
            return null
        }

        val packageName = packageName.asString()
        val functionName = simpleName.asString()
        return LabScreenEntry(
            title = title,
            order = order,
            qualifiedCall = if (packageName.isEmpty()) functionName else "$packageName.$functionName",
        )
    }

    /**
     * `Dependencies` is the part that is easy to get wrong and expensive to get wrong.
     *
     * It tells KSP which source files this output was derived from, so incremental
     * compilation can regenerate it when one of them changes. Passing `aggregating = true`
     * is correct here because the registry depends on *all* annotated files — adding a new
     * lab must regenerate it. Declaring `false` (isolating) would leave a stale registry
     * after a new screen is added, which looks like the annotation being ignored.
     */
    private fun writeRegistry(screens: List<LabScreenEntry>, sources: List<KSFile>) {
        val file = codeGenerator.createNewFile(
            dependencies = Dependencies(aggregating = true, *sources.toTypedArray()),
            packageName = PACKAGE,
            fileName = "GeneratedLabScreens",
        )

        OutputStreamWriter(file, Charsets.UTF_8).use { writer ->
            writer.write(buildSource(screens.sortedBy { it.order }))
        }
    }

    private fun buildSource(screens: List<LabScreenEntry>): String = buildString {
        appendLine("package $PACKAGE")
        appendLine()
        appendLine("import androidx.compose.runtime.Composable")
        appendLine()
        appendLine("// Generated by LabScreenProcessor. Do not edit.")
        appendLine("public data class LabScreenEntry(")
        appendLine("    public val title: String,")
        appendLine("    public val order: Int,")
        appendLine("    public val content: @Composable () -> Unit,")
        appendLine(")")
        appendLine()
        appendLine("public val generatedLabScreens: List<LabScreenEntry> = listOf(")
        screens.forEach { screen ->
            appendLine("    LabScreenEntry(")
            appendLine("        title = ${"\""}${screen.title}${"\""},")
            appendLine("        order = ${screen.order},")
            appendLine("        content = { ${screen.qualifiedCall}() },")
            appendLine("    ),")
        }
        appendLine(")")
    }

    private data class LabScreenEntry(
        val title: String,
        val order: Int,
        val qualifiedCall: String,
    )

    private companion object {
        const val PACKAGE = "hu.csabi.architecture.feature.labs"
        const val ANNOTATION_FQN = "hu.csabi.architecture.labs.LabScreen"
        const val ANNOTATION_SIMPLE_NAME = "LabScreen"
    }
}

private fun com.google.devtools.ksp.symbol.KSAnnotation.argument(name: String): Any? =
    arguments.firstOrNull { it.name?.asString() == name }?.value

/**
 * KSP discovers processors through this provider, registered in
 * `META-INF/services/com.google.devtools.ksp.processing.SymbolProcessorProvider`. Forgetting
 * that resource file is the classic "my processor never runs and nothing is logged" bug.
 */
class LabScreenProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor =
        LabScreenProcessor(environment.codeGenerator, environment.logger)
}

package hu.csabi.architecture.labs

/**
 * Lesson 14 — the annotation a KSP processor will read.
 *
 * `SOURCE` retention: it exists for the compiler and is not written into the class file at
 * all, because nothing reads it at runtime. An annotation kept at `RUNTIME` that nobody
 * reflects over is dead weight in the APK and a reason R8 has to keep metadata.
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.FUNCTION)
annotation class LabScreen(
    val title: String,
    val order: Int,
)

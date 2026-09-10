package hu.csabi.architecture.core.kotlinx

import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

/**
 * Lesson 01 — property delegation.
 *
 * `by` is pure convention: the compiler only looks for `getValue` / `setValue`. This is a
 * typed, map-backed config reader — the same mechanism behind `by lazy`, `by viewModels()`
 * and `by remember`.
 */
class ConfigSource(private val values: Map<String, String>) {

    fun string(default: String? = null): ReadOnlyProperty<Any?, String> =
        ReadOnlyProperty { _, property ->
            values[property.name]
                ?: default
                ?: error("Missing config key: ${property.name}")
        }

    fun int(default: Int? = null): ReadOnlyProperty<Any?, Int> =
        ReadOnlyProperty { _, property ->
            values[property.name]?.toIntOrNull()
                ?: default
                ?: error("Missing or invalid int config key: ${property.name}")
        }
}

/**
 * A hand-written delegate with `operator fun getValue`: computes once and caches, but
 * unlike `lazy` it can be reset.
 */
class ResettableLazy<T : Any>(private val initializer: () -> T) {
    @Volatile
    private var cached: T? = null

    operator fun getValue(thisRef: Any?, property: KProperty<*>): T =
        cached ?: synchronized(this) { cached ?: initializer().also { cached = it } }

    fun reset() {
        synchronized(this) { cached = null }
    }
}

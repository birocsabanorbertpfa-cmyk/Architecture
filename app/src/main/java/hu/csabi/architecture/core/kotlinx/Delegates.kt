package hu.csabi.architecture.core.kotlinx

import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

/**
 * Lesson 01 — property delegation.
 *
 * A `by` operátor mögött csak konvenció van: `getValue` / `setValue`.
 * Itt egy map-alapú, típusos konfiguráció-olvasó — ugyanez a minta hajtja a
 * `by viewModels()`, `by lazy`, `by remember` hívásokat is.
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
 * Saját delegate osztály `operator fun getValue`-val: egyszer számol, cache-el,
 * de a `lazy`-vel ellentétben `reset()`-elhető.
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

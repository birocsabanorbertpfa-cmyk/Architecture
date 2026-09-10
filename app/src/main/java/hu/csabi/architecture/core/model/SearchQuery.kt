package hu.csabi.architecture.core.model

/**
 * Lesson 01 — type-safe builder DSL.
 *
 * A GitHub search API egy stringet vár: `compose language:Kotlin stars:>=100`.
 * Stringet összefűzni hívó oldalon hibalehetőség; DSL-lel a szabályok egy helyen vannak.
 */
@DslMarker
annotation class SearchQueryDsl

/**
 * A `@DslMarker` megakadályozza, hogy egymásba ágyazott builder blokkokban véletlenül
 * a külső receiver metódusát hívd — implicit receiver scope-ot korlátoz.
 */
@SearchQueryDsl
class SearchQueryBuilder internal constructor() {
    private val terms = mutableListOf<String>()
    private val qualifiers = linkedMapOf<String, String>()

    fun term(value: String) {
        val trimmed = value.trim()
        if (trimmed.isNotEmpty()) terms += trimmed
    }

    fun language(value: String) {
        qualifiers["language"] = value
    }

    fun user(username: Username) {
        qualifiers["user"] = username.value
    }

    fun minStars(stars: Stars) {
        qualifiers["stars"] = ">=${stars.count}"
    }

    /** `infix`: `"topic" isEqualTo "android"` olvashatóbb a nyers map-írásnál. */
    infix fun String.isEqualTo(value: String) {
        qualifiers[this] = value
    }

    internal fun build(): SearchQuery {
        val raw = (terms + qualifiers.map { (key, value) -> "$key:$value" }).joinToString(" ")
        return SearchQuery(raw)
    }
}

@JvmInline
value class SearchQuery(val raw: String) {
    val isEmpty: Boolean get() = raw.isBlank()
}

/**
 * `block: SearchQueryBuilder.() -> Unit` = function type with receiver: a lambdán belül
 * `this` a builder, ezért írhatunk pontok nélkül. Ez adja a DSL-érzetet.
 *
 * Szándékosan NEM `inline`: a public inline függvény nem érhet el `internal` API-t
 * (itt a konstruktort és a `build()`-et) — vagy `@PublishedApi` kellene, vagy marad
 * a sima hívás. Egy builder esetén az inline amúgy sem nyerne semmit.
 */
fun searchQuery(block: SearchQueryBuilder.() -> Unit): SearchQuery =
    SearchQueryBuilder().apply(block).build()

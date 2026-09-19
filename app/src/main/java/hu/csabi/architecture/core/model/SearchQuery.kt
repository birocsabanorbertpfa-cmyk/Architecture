package hu.csabi.architecture.core.model

/**
 * Lesson 01 — type-safe builder DSL.
 *
 * The GitHub search API expects a single string: `compose language:Kotlin stars:>=100`.
 * Concatenating that at every call site invites typos; a DSL keeps the syntax rules in
 * one place and makes invalid queries hard to express.
 */
@DslMarker
annotation class SearchQueryDsl

/**
 * `@DslMarker` restricts implicit receivers: inside a nested builder block you cannot
 * accidentally call a method of the outer builder.
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

    /** `infix` for readability: `"topic" isEqualTo "android"` beats raw map writes. */
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
 * `block: SearchQueryBuilder.() -> Unit` is a function type with receiver: inside the
 * lambda `this` is the builder, which is what gives the DSL its shape.
 *
 * Deliberately not `inline`: a public inline function cannot access `internal` API (the
 * constructor and `build()` here) without `@PublishedApi`, and inlining a builder call
 * would buy nothing anyway.
 */
fun searchQuery(block: SearchQueryBuilder.() -> Unit): SearchQuery =
    SearchQueryBuilder().apply(block).build()

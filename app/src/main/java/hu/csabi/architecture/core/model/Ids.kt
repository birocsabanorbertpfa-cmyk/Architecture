package hu.csabi.architecture.core.model

import java.util.Locale

/**
 * Lesson 01 — value classes.
 *
 * At runtime these stay a plain `Long`/`String`/`Int` (no allocation), but at compile time
 * they are distinct types: a `RepoId` cannot be passed where `Stars` is expected. The
 * wrapper is only materialised when the value is used as a nullable or as a generic type
 * argument (`List<RepoId>` boxes).
 */
@JvmInline
value class RepoId(val value: Long) {
    init {
        require(value > 0) { "RepoId must be positive, was $value" }
    }
}

@JvmInline
value class Username(val value: String) {
    init {
        require(value.isNotBlank()) { "Username must not be blank" }
    }

    override fun toString(): String = "@$value"
}

@JvmInline
value class Stars(val count: Int) : Comparable<Stars> {
    override fun compareTo(other: Stars): Int = count.compareTo(other.count)

    /** 1234 -> "1.2k". Locale.US so the decimal separator does not depend on the device. */
    fun formatted(): String = when {
        count < 1_000 -> count.toString()
        count < 1_000_000 -> "%.1fk".format(Locale.US, count / 1_000.0)
        else -> "%.1fM".format(Locale.US, count / 1_000_000.0)
    }
}

package hu.csabi.architecture.core.model

import java.util.Locale

/**
 * Lesson 01 — value class.
 *
 * Runtime-ban `Long`/`String` marad (nincs allokáció), fordításkor viszont külön típus:
 * a `RepoId` és a `Stars` nem cserélhető össze. Az `inline` feloldódik, kivéve ha
 * nullable-ként vagy generikus paraméterként használjuk (`List<RepoId>` -> boxol).
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

    /** 1234 -> "1.2k" */
    fun formatted(): String = when {
        count < 1_000 -> count.toString()
        count < 1_000_000 -> "%.1fk".format(Locale.US, count / 1_000.0)
        else -> "%.1fM".format(Locale.US, count / 1_000_000.0)
    }
}

package hu.csabi.architecture.core

import hu.csabi.architecture.core.model.Stars
import hu.csabi.architecture.core.model.Username
import hu.csabi.architecture.core.model.searchQuery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchQueryTest {

    @Test
    fun `builds github search syntax in a stable order`() {
        val query = searchQuery {
            term("  compose  ")
            language("Kotlin")
            minStars(Stars(100))
            user(Username("android"))
            "topic" isEqualTo "ui"
        }

        assertEquals("compose language:Kotlin stars:>=100 user:android topic:ui", query.raw)
    }

    @Test
    fun `blank terms are ignored`() {
        assertTrue(searchQuery { term("   ") }.isEmpty)
    }

    @Test
    fun `stars formatting shortens large numbers`() {
        assertEquals("999", Stars(999).formatted())
        assertEquals("1.2k", Stars(1_234).formatted())
        assertEquals("2.0M", Stars(2_000_000).formatted())
    }
}

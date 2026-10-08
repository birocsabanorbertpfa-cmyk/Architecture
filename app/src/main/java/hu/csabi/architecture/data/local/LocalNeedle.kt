package hu.csabi.architecture.data.local

import hu.csabi.architecture.domain.model.SearchQuery

/**
 * Lesson 09 — one definition of "what this query matches locally".
 *
 * The GitHub query string carries qualifiers (`language:Kotlin stars:>=100`) that SQLite
 * knows nothing about, so local matching uses the free-text term only. Both the repository
 * and the paging mediator need the exact same rule, and two copies of it would drift — hence
 * one `internal` extension in the data layer, where this decision belongs.
 */
internal fun SearchQuery.localNeedle(): String = raw.substringBefore(' ')

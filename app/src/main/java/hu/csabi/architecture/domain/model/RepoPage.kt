package hu.csabi.architecture.domain.model

/**
 * Lesson 09 — a page is more than the rows on it.
 *
 * Paging needs to know when to stop, and that answer lives in the response envelope, not in
 * the items: GitHub reports [totalCount] alongside the results. Lesson 04's data source
 * threw that away because nothing needed it yet; adding it now is a good illustration of why
 * the DTO layer exists — the information was always on the wire, only unmapped.
 */
data class RepoPage(
    val repos: List<Repo>,
    val totalCount: Int,
)

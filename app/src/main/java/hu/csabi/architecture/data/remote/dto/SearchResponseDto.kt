package hu.csabi.architecture.data.remote.dto

import hu.csabi.architecture.core.model.Repo
import hu.csabi.architecture.core.model.RepoId
import hu.csabi.architecture.core.model.Stars
import hu.csabi.architecture.core.model.Username
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Lesson 04 — DTOs mirror the wire format, nothing else.
 *
 * They are a separate layer from the domain model on purpose:
 *  - the JSON shape is the backend's decision and can change without notice
 *  - fields the app does not care about never spread past this file
 *  - the domain model stays free of `@Serializable` and of nullable-everything types
 *
 * kotlinx.serialization generates the parser at **compile time** (via the compiler plugin),
 * so unlike Gson/Moshi-reflect there is no reflection at runtime and no R8 keep rules.
 */
@Serializable
data class SearchResponseDto(
    @SerialName("total_count") val totalCount: Int,
    @SerialName("incomplete_results") val incompleteResults: Boolean = false,
    val items: List<RepoDto> = emptyList(),
)

@Serializable
data class RepoDto(
    val id: Long,
    val name: String,
    val owner: OwnerDto,
    // Nullable in the API, so nullable here. The domain model decides what to do about it.
    val description: String? = null,
    @SerialName("stargazers_count") val stargazersCount: Int = 0,
    val language: String? = null,
    @SerialName("html_url") val htmlUrl: String? = null,
)

@Serializable
data class OwnerDto(
    val login: String,
    @SerialName("avatar_url") val avatarUrl: String? = null,
)

/**
 * The mapping boundary. Every invalid-but-parseable payload is rejected here rather than
 * downstream: `RepoId` and `Username` both throw on invalid input, so a malformed record
 * cannot enter the domain at all.
 */
fun RepoDto.toDomain(): Repo = Repo(
    id = RepoId(id),
    name = name,
    owner = Username(owner.login),
    description = description?.takeIf { it.isNotBlank() },
    stars = Stars(stargazersCount),
    language = language,
)

/**
 * A single bad item should not sink the whole list, so mapping failures are dropped instead
 * of thrown. In production this is where you would report the dropped records.
 */
fun SearchResponseDto.toDomain(): List<Repo> = items.mapNotNull { dto ->
    runCatching { dto.toDomain() }.getOrNull()
}

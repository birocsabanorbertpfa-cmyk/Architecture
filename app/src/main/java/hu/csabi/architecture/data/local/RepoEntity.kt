package hu.csabi.architecture.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import hu.csabi.architecture.domain.model.Repo
import hu.csabi.architecture.domain.model.RepoId
import hu.csabi.architecture.domain.model.Stars
import hu.csabi.architecture.domain.model.Username

/**
 * Lesson 08 — a third model, for the same reason there is a DTO.
 *
 * The database schema is a persistence concern: a primary key, column names that must stay
 * stable across migrations, and a `fetched_at` column that means nothing to the domain.
 * Reusing [Repo] here would turn every domain rename into a migration, and leak storage
 * details upwards.
 *
 * Three models, three reasons: the DTO mirrors the wire, the entity mirrors the table, the
 * domain mirrors the problem. The mappers below are the only code that knows two of them.
 */
@Entity(
    tableName = "repos",
    // Index the columns actually filtered or sorted on. Without these, every search scans
    // the whole table; with them SQLite walks an index instead.
    indices = [Index(value = ["language"]), Index(value = ["stars"])],
)
data class RepoEntity(
    @PrimaryKey val id: Long,
    val name: String,
    val owner: String,
    val description: String?,
    val stars: Int,
    val language: String?,

    /**
     * When this row was written. This single column is what makes an offline-first decision
     * possible: "is the cache fresh enough to skip the network?" needs a timestamp, not a
     * guess. Added in schema version 2, which is why there is a migration.
     */
    @ColumnInfo(name = "fetched_at") val fetchedAt: Long,
)

/**
 * The value classes are unwrapped here on purpose.
 *
 * A `TypeConverter` could persist `RepoId` directly, but then the column type depends on a
 * domain class, and changing that class silently changes the schema. Mapping explicitly
 * keeps the table independent of the domain's shape — the same reason the DTO uses plain
 * `Long` fields.
 */
fun RepoEntity.toDomain(): Repo = Repo(
    id = RepoId(id),
    name = name,
    owner = Username(owner),
    description = description,
    stars = Stars(stars),
    language = language,
)

fun Repo.toEntity(fetchedAt: Long): RepoEntity = RepoEntity(
    id = id.value,
    name = name,
    owner = owner.value,
    description = description,
    stars = stars.count,
    language = language,
    fetchedAt = fetchedAt,
)

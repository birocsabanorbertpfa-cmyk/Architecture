package hu.csabi.architecture.data.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert

/**
 * Lesson 09 — pagination state is data, so it is persisted like data.
 *
 * The mediator needs to answer "which page comes next?" after a process restart, when
 * nothing is left in memory. Keeping that in a field would mean the list silently reloads
 * from page 1 every time the app is killed; keeping it in a table makes it survive.
 *
 * Keyed by the query string, because two different searches page independently.
 */
@Entity(tableName = "remote_keys")
data class RemoteKeyEntity(
    @PrimaryKey @ColumnInfo(name = "query_key") val queryKey: String,
    /** `null` means the previous page was the last one. */
    @ColumnInfo(name = "next_page") val nextPage: Int?,
    @ColumnInfo(name = "total_count") val totalCount: Int,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Dao
interface RemoteKeyDao {

    @Query("SELECT * FROM remote_keys WHERE query_key = :queryKey")
    suspend fun find(queryKey: String): RemoteKeyEntity?

    @Upsert
    suspend fun upsert(key: RemoteKeyEntity)

    @Query("DELETE FROM remote_keys WHERE query_key = :queryKey")
    suspend fun delete(queryKey: String)
}

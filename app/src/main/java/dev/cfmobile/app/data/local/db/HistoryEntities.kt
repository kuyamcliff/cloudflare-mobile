package dev.cfmobile.app.data.local.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * One Cloudflare API call, as metadata only (spec 68). There is deliberately no column that
 * could hold a header or a body, so a token or secret cannot end up here even by mistake.
 */
@Entity(tableName = "request_history", indices = [Index("profileId", "timestamp")])
data class RequestHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: String,
    val method: String,
    val path: String,
    val query: String?,
    val statusCode: Int?,
    val durationMillis: Long,
    val timestamp: Long,
    val errorClass: String?
)

@Dao
interface RequestHistoryDao {
    @Insert
    suspend fun insert(entity: RequestHistoryEntity)

    @Query("SELECT * FROM request_history WHERE profileId = :profileId ORDER BY timestamp DESC LIMIT :limit")
    fun observe(profileId: String, limit: Int): Flow<List<RequestHistoryEntity>>

    /** Mutations only: the local activity timeline (spec 69) is built from these. */
    @Query("SELECT * FROM request_history WHERE profileId = :profileId AND method != 'GET' ORDER BY timestamp DESC LIMIT :limit")
    fun observeMutations(profileId: String, limit: Int): Flow<List<RequestHistoryEntity>>

    @Query("DELETE FROM request_history WHERE profileId = :profileId")
    suspend fun clear(profileId: String)

    @Query("DELETE FROM request_history")
    suspend fun clearAll()

    @Query("DELETE FROM request_history WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)
}

/** A request template the user saved from the API Explorer or GraphQL console. Holds no
 *  credential: the active profile's token is injected when it runs (spec 163, 269). */
@Entity(tableName = "saved_requests", indices = [Index("profileId")])
data class SavedRequestEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: String,
    val kind: String,
    val name: String,
    val method: String,
    val path: String,
    val query: String?,
    val body: String?,
    val createdAt: Long
)

@Dao
interface SavedRequestDao {
    @Insert
    suspend fun insert(entity: SavedRequestEntity): Long

    @Query("SELECT * FROM saved_requests WHERE profileId = :profileId AND kind = :kind ORDER BY createdAt DESC")
    fun observe(profileId: String, kind: String): Flow<List<SavedRequestEntity>>

    @Query("DELETE FROM saved_requests WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM saved_requests")
    suspend fun clearAll()
}

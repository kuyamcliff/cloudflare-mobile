package dev.cfmobile.app.data.local.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

enum class TransferDirection { UPLOAD, DOWNLOAD }

enum class TransferState { QUEUED, RUNNING, PAUSED, COMPLETED, FAILED, CANCELED }

/**
 * A persisted upload or download (spec 184). Every field a worker needs to resume after
 * process death is here, and the target context (profile, account, bucket, key) is captured
 * at creation so switching accounts in the UI never redirects a running job (spec 202).
 */
@Entity(tableName = "transfers")
data class TransferEntity(
    @PrimaryKey val id: String,
    val profileId: String,
    val direction: TransferDirection,
    val accountId: String,
    val bucket: String,
    val jurisdiction: String?,
    val objectKey: String,
    /** content:// URI of the local file (source for uploads, destination for downloads). */
    val localUri: String,
    val displayName: String,
    val mimeType: String?,
    val totalBytes: Long,
    val transferredBytes: Long,
    val state: TransferState,
    /** `rest` for the single-request REST API, `s3` for resumable S3 multipart. */
    val method: String,
    val multipartUploadId: String?,
    val partSizeBytes: Long,
    val wifiOnly: Boolean,
    val retryCount: Int,
    val error: String?,
    val createdAt: Long,
    val updatedAt: Long
)

/** A completed multipart part: its number and the ETag R2 returned, needed to complete the
 *  upload and to skip already-sent parts on resume. */
@Entity(tableName = "transfer_parts", primaryKeys = ["transferId", "partNumber"])
data class TransferPartEntity(
    val transferId: String,
    val partNumber: Int,
    val etag: String,
    val size: Long
)

@Dao
interface TransferDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TransferEntity)

    @Update
    suspend fun update(entity: TransferEntity)

    @Query("SELECT * FROM transfers WHERE id = :id")
    suspend fun get(id: String): TransferEntity?

    @Query("SELECT * FROM transfers ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<TransferEntity>>

    @Query("UPDATE transfers SET transferredBytes = :bytes, updatedAt = :now WHERE id = :id")
    suspend fun updateProgress(id: String, bytes: Long, now: Long)

    @Query("UPDATE transfers SET state = :state, error = :error, updatedAt = :now WHERE id = :id")
    suspend fun updateState(id: String, state: TransferState, error: String?, now: Long)

    @Query("DELETE FROM transfers WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM transfers WHERE state IN ('COMPLETED','CANCELED','FAILED')")
    suspend fun clearFinished()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPart(part: TransferPartEntity)

    @Query("SELECT * FROM transfer_parts WHERE transferId = :transferId ORDER BY partNumber")
    suspend fun parts(transferId: String): List<TransferPartEntity>

    @Query("DELETE FROM transfer_parts WHERE transferId = :transferId")
    suspend fun deleteParts(transferId: String)
}

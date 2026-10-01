package dev.cfmobile.app.core.transfers

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dev.cfmobile.app.data.local.db.TransferDao
import dev.cfmobile.app.data.local.db.TransferDirection
import dev.cfmobile.app.data.local.db.TransferEntity
import dev.cfmobile.app.data.local.db.TransferState
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import java.util.concurrent.TimeUnit

data class PickedFile(val uri: Uri, val name: String, val size: Long, val mimeType: String?)

/** Why an upload cannot be queued as requested, decided before anything is sent. */
class TransferRejected(message: String) : IllegalArgumentException(message)

/**
 * Queues and controls transfers (spec 36, 92, 183). Each job is persisted first, then run by
 * [TransferWorker] under WorkManager so it survives process death; the target account,
 * bucket and key are fixed at creation (spec 202).
 */
class TransferRepository(
    private val context: Context,
    private val dao: TransferDao,
    private val credentials: R2CredentialStore,
    private val workManager: () -> WorkManager = { WorkManager.getInstance(context) }
) {
    fun observe(): Flow<List<TransferEntity>> = dao.observeAll()

    fun describe(resolver: ContentResolver, uri: Uri): PickedFile {
        var name = uri.lastPathSegment ?: "file"
        var size = -1L
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 && !c.isNull(it) }?.let { name = c.getString(it) }
                c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 && !c.isNull(it) }?.let { size = c.getLong(it) }
            }
        }
        return PickedFile(uri, name, size, resolver.getType(uri))
    }

    /** Picks the upload path: REST up to Cloudflare's 300 MB limit, S3 multipart above it or
     *  whenever R2 S3 credentials exist for large files (resumable). */
    fun planMethod(profileId: String, accountId: String, size: Long): String {
        val hasS3 = credentials.has(profileId, accountId)
        return when {
            size < 0 && !hasS3 -> throw TransferRejected("The file size is unknown. Add R2 S3 credentials to upload it with multipart.")
            size > REST_MAX_BYTES && !hasS3 -> throw TransferRejected("Files over 300 MB need R2 S3 credentials for multipart upload. Add them in the bucket's settings.")
            hasS3 && (size < 0 || size >= MULTIPART_THRESHOLD) -> METHOD_S3
            else -> METHOD_REST
        }
    }

    suspend fun enqueueUploads(
        profileId: String,
        accountId: String,
        bucket: String,
        jurisdiction: String?,
        prefix: String,
        files: List<PickedFile>,
        wifiOnly: Boolean
    ): List<String> {
        val now = System.currentTimeMillis()
        val ids = mutableListOf<String>()
        for (f in files) {
            val method = planMethod(profileId, accountId, f.size)
            runCatching { context.contentResolver.takePersistableUriPermission(f.uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            val id = UUID.randomUUID().toString()
            dao.upsert(
                TransferEntity(
                    id = id, profileId = profileId, direction = TransferDirection.UPLOAD, accountId = accountId, bucket = bucket,
                    jurisdiction = jurisdiction, objectKey = prefix + f.name, localUri = f.uri.toString(), displayName = f.name,
                    mimeType = f.mimeType, totalBytes = f.size, transferredBytes = 0, state = TransferState.QUEUED, method = method,
                    multipartUploadId = null, partSizeBytes = partSizeFor(f.size), wifiOnly = wifiOnly, retryCount = 0,
                    error = null, createdAt = now, updatedAt = now
                )
            )
            schedule(id, wifiOnly)
            ids += id
        }
        return ids
    }

    suspend fun enqueueDownload(
        profileId: String, accountId: String, bucket: String, jurisdiction: String?, key: String,
        size: Long, mimeType: String?, destination: Uri, wifiOnly: Boolean
    ): String {
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        runCatching {
            context.contentResolver.takePersistableUriPermission(destination, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        dao.upsert(
            TransferEntity(
                id = id, profileId = profileId, direction = TransferDirection.DOWNLOAD, accountId = accountId, bucket = bucket,
                jurisdiction = jurisdiction, objectKey = key, localUri = destination.toString(), displayName = key.substringAfterLast('/'),
                mimeType = mimeType, totalBytes = size, transferredBytes = 0, state = TransferState.QUEUED,
                method = if (credentials.has(profileId, accountId)) METHOD_S3 else METHOD_REST,
                multipartUploadId = null, partSizeBytes = 0, wifiOnly = wifiOnly, retryCount = 0, error = null,
                createdAt = now, updatedAt = now
            )
        )
        schedule(id, wifiOnly)
        return id
    }

    private fun schedule(id: String, wifiOnly: Boolean) {
        val request = OneTimeWorkRequestBuilder<TransferWorker>()
            .setInputData(workDataOf(TransferWorker.KEY_ID to id))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .addTag(TAG)
            .build()
        workManager().enqueueUniqueWork(workName(id), ExistingWorkPolicy.REPLACE, request)
    }

    suspend fun pause(id: String) {
        dao.updateState(id, TransferState.PAUSED, null, System.currentTimeMillis())
        workManager().cancelUniqueWork(workName(id))
    }

    suspend fun resume(id: String) {
        val t = dao.get(id) ?: return
        dao.updateState(id, TransferState.QUEUED, null, System.currentTimeMillis())
        schedule(id, t.wifiOnly)
    }

    /** Cancels and, for multipart, aborts the upload in R2 so no orphaned parts remain. */
    suspend fun cancel(id: String, s3: R2S3Client) {
        val t = dao.get(id) ?: return
        dao.updateState(id, TransferState.CANCELED, null, System.currentTimeMillis())
        workManager().cancelUniqueWork(workName(id))
        val uploadId = t.multipartUploadId
        if (t.direction == TransferDirection.UPLOAD && uploadId != null) {
            credentials.get(t.profileId, t.accountId)?.let { creds ->
                runCatching { s3.abortMultipartUpload(creds, t.accountId, t.bucket, t.objectKey, uploadId) }
            }
            dao.deleteParts(id)
        }
    }

    suspend fun remove(id: String) {
        workManager().cancelUniqueWork(workName(id))
        dao.deleteParts(id)
        dao.delete(id)
    }

    suspend fun clearFinished() = dao.clearFinished()

    companion object {
        const val TAG = "cf-transfer"
        const val METHOD_REST = "rest"
        const val METHOD_S3 = "s3"
        const val REST_MAX_BYTES = 300L * 1000 * 1000
        const val MULTIPART_THRESHOLD = 64L * 1024 * 1024
        private const val MIN_PART = 8L * 1024 * 1024
        private const val MAX_PARTS = 10_000

        fun workName(id: String) = "transfer-$id"

        /** 8 MiB parts, grown in whole MiB so no upload exceeds S3's 10,000-part limit. */
        fun partSizeFor(size: Long): Long {
            if (size <= 0) return 16L * 1024 * 1024
            val needed = (size + MAX_PARTS - 1) / MAX_PARTS
            val mib = 1024L * 1024
            return maxOf(MIN_PART, ((needed + mib - 1) / mib) * mib)
        }
    }
}

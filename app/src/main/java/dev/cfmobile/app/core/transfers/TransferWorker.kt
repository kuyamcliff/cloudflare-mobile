package dev.cfmobile.app.core.transfers

import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import dev.cfmobile.app.CfApplication
import dev.cfmobile.app.data.local.db.TransferDirection
import dev.cfmobile.app.data.local.db.TransferEntity
import dev.cfmobile.app.data.local.db.TransferPartEntity
import dev.cfmobile.app.data.local.db.TransferState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicLong

/**
 * Runs one persisted transfer (spec 36, 137, 183). Progress is real: bytes counted as they
 * are written to or read from the socket, flushed to the database twice a second (spec 185).
 *
 * Retry policy (spec 331): network failures, 5xx and 429 are retried by WorkManager with
 * exponential backoff; 400/401/403/404 and local file problems fail immediately.
 */
class TransferWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    private val container get() = (applicationContext as CfApplication).container
    private val dao get() = container.database.transferDao()

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val t = inputData.getString(KEY_ID)?.let { dao.get(it) }
        return foregroundInfo(t?.displayName ?: "Transfer", t?.transferredBytes ?: 0, t?.totalBytes ?: -1)
    }

    private fun foregroundInfo(title: String, done: Long, total: Long): ForegroundInfo {
        TransferNotifications.ensureChannels(applicationContext)
        val notification = TransferNotifications.progress(applicationContext, title, done, total)
        val id = NOTIFICATION_BASE + (inputData.getString(KEY_ID)?.hashCode() ?: 0) % 10_000
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ForegroundInfo(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else ForegroundInfo(id, notification)
    }

    override suspend fun doWork(): Result {
        val id = inputData.getString(KEY_ID) ?: return Result.failure()
        val t = dao.get(id) ?: return Result.success()
        if (t.state == TransferState.CANCELED || t.state == TransferState.PAUSED || t.state == TransferState.COMPLETED) return Result.success()
        if (container.accountStore.getAll().none { it.id == t.profileId }) {
            fail(t, "The profile that started this transfer was removed from this device.")
            return Result.failure()
        }
        dao.updateState(id, TransferState.RUNNING, null, now())
        runCatching { setForeground(foregroundInfo(t.displayName, t.transferredBytes, t.totalBytes)) }

        val counter = AtomicLong(0)
        val base = startingBytes(t)
        dao.updateProgress(id, base, now())
        return try {
            coroutineScope {
                val reporter = launch { reportProgress(t, base, counter) }
                try {
                    when (t.direction) {
                        TransferDirection.UPLOAD -> if (t.method == TransferRepository.METHOD_S3) uploadMultipart(t, counter) else uploadRest(t, counter)
                        TransferDirection.DOWNLOAD -> download(t, counter)
                    }
                } finally {
                    reporter.cancel()
                }
                dao.updateProgress(id, base + counter.get(), now())
            }
            val final = dao.get(id) ?: return Result.success()
            dao.upsert(final.copy(state = TransferState.COMPLETED, transferredBytes = maxOf(final.transferredBytes, final.totalBytes), error = null, updatedAt = now()))
            TransferNotifications.result(applicationContext, id.hashCode(), if (t.direction == TransferDirection.UPLOAD) "Upload complete" else "Download complete", t.displayName)
            Result.success()
        } catch (e: CancellationException) {
            // Paused or canceled by the user (state already set), or stopped by the system,
            // in which case WorkManager reschedules and the job resumes from persisted parts.
            val current = dao.get(id)
            if (current?.state == TransferState.RUNNING) dao.updateState(id, TransferState.QUEUED, null, now())
            throw e
        } catch (e: PermanentTransferError) {
            fail(t, e.message ?: "Transfer failed")
            Result.failure()
        } catch (e: S3Exception) {
            if (e.status in RETRYABLE || e.status >= 500) retryOrFail(t, e.message ?: "R2 error") else { fail(t, e.message ?: "R2 error"); Result.failure() }
        } catch (e: IOException) {
            retryOrFail(t, "Network error: ${e.message ?: "connection lost"}")
        } catch (e: SecurityException) {
            fail(t, "Access to the local file was revoked. Pick the file again.")
            Result.failure()
        }
    }

    private suspend fun retryOrFail(t: TransferEntity, message: String): Result {
        val current = dao.get(t.id) ?: return Result.failure()
        return if (runAttemptCount < MAX_ATTEMPTS) {
            dao.upsert(current.copy(state = TransferState.QUEUED, retryCount = current.retryCount + 1, error = "Retrying: $message", updatedAt = now()))
            Result.retry()
        } else {
            fail(current, message)
            Result.failure()
        }
    }

    private suspend fun fail(t: TransferEntity, message: String) {
        dao.updateState(t.id, TransferState.FAILED, message, now())
        TransferNotifications.result(applicationContext, t.id.hashCode(), "Transfer failed", "${t.displayName}: $message")
    }

    /** Bytes already safely transferred: completed multipart parts, or the length of a
     *  partial download that can resume with a ranged request. Everything else restarts. */
    private suspend fun startingBytes(t: TransferEntity): Long = when {
        t.direction == TransferDirection.UPLOAD && t.method == TransferRepository.METHOD_S3 -> dao.parts(t.id).sumOf { it.size }
        t.direction == TransferDirection.DOWNLOAD && t.method == TransferRepository.METHOD_S3 &&
            container.r2Credentials.has(t.profileId, t.accountId) -> existingLength(Uri.parse(t.localUri))
        else -> 0L
    }

    /** What is actually on disk, not what was counted, so a resume never leaves a gap. */
    private fun existingLength(uri: Uri): Long = runCatching {
        applicationContext.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize }
    }.getOrNull()?.takeIf { it > 0 } ?: 0L

    private suspend fun reportProgress(t: TransferEntity, base: Long, counter: AtomicLong) = coroutineScope {
        while (isActive) {
            delay(500)
            val done = base + counter.get()
            dao.updateProgress(t.id, done, now())
            runCatching { setForeground(foregroundInfo(t.displayName, done, t.totalBytes)) }
        }
    }

    private suspend fun uploadRest(t: TransferEntity, counter: AtomicLong) {
        if (t.totalBytes < 0) throw PermanentTransferError("File size unknown")
        val body = FileRangeRequestBody(applicationContext.contentResolver, Uri.parse(t.localUri), 0, t.totalBytes, t.mimeType?.toMediaTypeOrNull()) { counter.addAndGet(it) }
        val response = container.r2ObjectsRepository.putObjectRaw(t.accountId, t.bucket, t.objectKey, body, t.jurisdiction)
        when {
            response.isSuccess -> {
                val etag = response.etag?.trim('"')
                val local = body.md5Hex
                if (etag != null && local != null && etag.length == 32 && !etag.equals(local, ignoreCase = true)) {
                    throw PermanentTransferError("Checksum mismatch: R2 stored a different file than was read locally")
                }
            }
            response.status == 429 || (response.status ?: 0) >= 500 || response.status == null -> throw IOException(response.message)
            else -> throw PermanentTransferError(response.message)
        }
    }

    private suspend fun uploadMultipart(start: TransferEntity, counter: AtomicLong) {
        val creds = container.r2Credentials.get(start.profileId, start.accountId)
            ?: throw PermanentTransferError("R2 S3 credentials were removed. Add them again to resume.")
        val s3 = container.r2S3Client
        var t = start
        val uploadId = t.multipartUploadId ?: s3.createMultipartUpload(creds, t.accountId, t.bucket, t.objectKey, t.mimeType).also { id ->
            t = t.copy(multipartUploadId = id, updatedAt = now())
            dao.upsert(t)
        }
        val size = t.totalBytes
        if (size <= 0) throw PermanentTransferError("Multipart needs a known, non-empty file size")
        val partSize = t.partSizeBytes
        val partCount = ((size + partSize - 1) / partSize).toInt()
        val done = dao.parts(t.id).associateBy { it.partNumber }

        val concurrency = container.settings.state.value.transferConcurrency
        val gate = Semaphore(concurrency)
        coroutineScope {
            for (n in 1..partCount) {
                if (n in done) continue
                launch {
                    gate.withPermit {
                        val offset = (n - 1).toLong() * partSize
                        val length = minOf(partSize, size - offset)
                        var sentForThisAttempt = 0L
                        val body = FileRangeRequestBody(applicationContext.contentResolver, Uri.parse(t.localUri), offset, length, null) {
                            sentForThisAttempt += it
                            counter.addAndGet(it)
                        }
                        val etag = try {
                            s3.uploadPart(creds, t.accountId, t.bucket, t.objectKey, uploadId, n, body)
                        } catch (e: IOException) {
                            // Undo this attempt's partial count so progress never overstates.
                            counter.addAndGet(-sentForThisAttempt)
                            throw e
                        }
                        val local = body.md5Hex
                        if (local != null && !etag.equals(local, ignoreCase = true)) {
                            throw PermanentTransferError("Checksum mismatch on part $n")
                        }
                        dao.upsertPart(TransferPartEntity(t.id, n, etag, length))
                    }
                }
            }
        }
        val parts = dao.parts(t.id)
        if (parts.size != partCount) throw IOException("Only ${parts.size} of $partCount parts uploaded")
        s3.completeMultipartUpload(creds, t.accountId, t.bucket, t.objectKey, uploadId, parts.map { it.partNumber to it.etag })
        dao.deleteParts(t.id)
    }

    private suspend fun download(t: TransferEntity, counter: AtomicLong) {
        val resolver = applicationContext.contentResolver
        val dest = Uri.parse(t.localUri)
        val creds = if (t.method == TransferRepository.METHOD_S3) container.r2Credentials.get(t.profileId, t.accountId) else null
        val resumeFrom = if (creds != null) existingLength(dest) else 0L
        if (t.totalBytes > 0 && resumeFrom >= t.totalBytes) return

        val md5 = if (resumeFrom == 0L) MessageDigest.getInstance("MD5") else null
        var etag: String? = null
        val stream = if (creds != null) {
            val r = container.r2S3Client.getObject(creds, t.accountId, t.bucket, t.objectKey, resumeFrom)
            etag = r.header("ETag")?.trim('"')
            r.body.byteStream()
        } else {
            val r = container.r2ObjectsRepository.openObject(t.accountId, t.bucket, t.objectKey, t.jurisdiction)
            etag = r.etag?.trim('"')
            r.stream
        }
        stream.use { input ->
            val out = resolver.openOutputStream(dest, if (resumeFrom > 0) "wa" else "wt") ?: throw PermanentTransferError("Could not open the destination file")
            out.use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    md5?.update(buffer, 0, read)
                    counter.addAndGet(read.toLong())
                }
            }
        }
        val local = md5?.digest()?.joinToString("") { "%02x".format(it) }
        if (local != null && etag != null && etag.length == 32 && !etag.equals(local, ignoreCase = true)) {
            throw PermanentTransferError("Checksum mismatch: the downloaded file does not match R2's ETag")
        }
    }

    private fun now() = System.currentTimeMillis()

    class PermanentTransferError(message: String) : Exception(message)

    companion object {
        const val KEY_ID = "transfer_id"
        private const val MAX_ATTEMPTS = 6
        private const val NOTIFICATION_BASE = 40_000
        private val RETRYABLE = setOf(408, 429)
    }
}

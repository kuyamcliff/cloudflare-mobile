package dev.cfmobile.app.ui.r2

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cfmobile.app.core.errors.ClassifiedError
import dev.cfmobile.app.core.errors.ErrorClassifier
import dev.cfmobile.app.core.net.ConnectionState
import dev.cfmobile.app.core.security.AppSettingsSnapshot
import dev.cfmobile.app.core.transfers.PickedFile
import dev.cfmobile.app.core.transfers.R2CredentialStore
import dev.cfmobile.app.core.transfers.R2S3Client
import dev.cfmobile.app.core.transfers.R2S3Credentials
import dev.cfmobile.app.core.transfers.TransferRejected
import dev.cfmobile.app.core.transfers.TransferRepository
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.dto.R2Object
import dev.cfmobile.app.data.repository.R2ObjectsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

enum class ObjectSort(val label: String) { NAME("Name"), SIZE("Size"), MODIFIED("Modified") }

sealed class Preview {
    data class Text(val key: String, val text: String, val truncated: Boolean) : Preview()
    data class Image(val key: String, val bitmap: Bitmap) : Preview()
    data class Unsupported(val key: String, val reason: String) : Preview()
    data class Loading(val key: String) : Preview()
}

data class UploadPlan(val files: List<PickedFile>, val methods: List<String>, val problems: List<String>, val totalBytes: Long, val needsMobileConfirm: Boolean)

data class R2ObjectsUiState(
    val prefix: String = "",
    val search: String = "",
    val folders: List<String> = emptyList(),
    val objects: List<R2Object> = emptyList(),
    val cursor: String? = null,
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val error: ClassifiedError? = null,
    val selected: Set<String> = emptySet(),
    val sort: ObjectSort = ObjectSort.NAME,
    val preview: Preview? = null,
    val uploadPlan: UploadPlan? = null,
    val message: String? = null,
    val hasS3Credentials: Boolean = false,
    val deleting: Boolean = false
) {
    val sortedObjects: List<R2Object>
        get() = when (sort) {
            ObjectSort.NAME -> objects.sortedBy { it.key.lowercase() }
            ObjectSort.SIZE -> objects.sortedByDescending { it.sizeBytes }
            ObjectSort.MODIFIED -> objects.sortedByDescending { it.lastModified.orEmpty() }
        }
}

@OptIn(FlowPreview::class)
class R2ObjectsViewModel(
    val accountId: String,
    val bucket: String,
    val jurisdiction: String?,
    private val profileId: String,
    private val repository: R2ObjectsRepository,
    private val transfers: TransferRepository,
    private val credentials: R2CredentialStore,
    private val s3: R2S3Client,
    private val settings: () -> AppSettingsSnapshot,
    private val connection: () -> ConnectionState,
    private val cacheDir: java.io.File
) : ViewModel() {
    private val _uiState = MutableStateFlow(R2ObjectsUiState(hasS3Credentials = credentials.has(profileId, accountId)))
    val uiState: StateFlow<R2ObjectsUiState> = _uiState.asStateFlow()
    private var loadJob: Job? = null
    private var previewJob: Job? = null

    init { load(reset = true) }

    private val listPrefix: String get() = _uiState.value.prefix + _uiState.value.search

    fun load(reset: Boolean) {
        loadJob?.cancel()
        val cursor = if (reset) null else _uiState.value.cursor
        _uiState.update { if (reset) it.copy(loading = true, error = null, cursor = null) else it.copy(loadingMore = true) }
        loadJob = viewModelScope.launch {
            when (val r = repository.list(accountId, bucket, listPrefix, cursor, jurisdiction)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(
                        loading = false, loadingMore = false,
                        folders = if (reset) r.data.folders else (it.folders + r.data.folders).distinct(),
                        objects = if (reset) r.data.objects else it.objects + r.data.objects,
                        cursor = if (r.data.truncated) r.data.cursor else null,
                        selected = if (reset) emptySet() else it.selected
                    )
                }
                is ApiResult.Failure -> _uiState.update { it.copy(loading = false, loadingMore = false, error = ErrorClassifier.classify(r)) }
            }
        }
    }

    fun openFolder(folder: String) { _uiState.update { it.copy(prefix = folder, search = "") }; load(true) }
    fun goTo(prefix: String) { _uiState.update { it.copy(prefix = prefix, search = "") }; load(true) }
    fun up(): Boolean {
        val p = _uiState.value.prefix
        if (p.isEmpty()) return false
        goTo(p.trimEnd('/').substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" })
        return true
    }

    /** Server-side prefix search within the current folder (spec 156, 324). */
    fun setSearch(q: String) {
        _uiState.update { it.copy(search = q) }
        load(true)
    }

    fun setSort(s: ObjectSort) = _uiState.update { it.copy(sort = s) }
    fun toggleSelect(key: String) = _uiState.update { it.copy(selected = if (key in it.selected) it.selected - key else it.selected + key) }
    fun selectAll() = _uiState.update { it.copy(selected = it.objects.map { o -> o.key }.toSet()) }
    fun clearSelection() = _uiState.update { it.copy(selected = emptySet()) }
    fun dismissMessage() = _uiState.update { it.copy(message = null) }

    fun deleteSelected() {
        val keys = _uiState.value.selected.toList()
        if (keys.isEmpty()) return
        _uiState.update { it.copy(deleting = true) }
        viewModelScope.launch {
            val results = repository.deleteMany(accountId, bucket, keys, jurisdiction)
            val failed = results.filterValues { it is ApiResult.Failure }
            _uiState.update {
                it.copy(
                    deleting = false,
                    selected = failed.keys,
                    message = if (failed.isEmpty()) "Deleted ${keys.size} object${if (keys.size == 1) "" else "s"}"
                    else "${keys.size - failed.size} deleted, ${failed.size} failed: ${(failed.values.first() as ApiResult.Failure).message}"
                )
            }
            load(true)
        }
    }

    fun planUpload(files: List<PickedFile>) {
        val methods = mutableListOf<String>()
        val problems = mutableListOf<String>()
        files.forEach { f ->
            try {
                methods += transfers.planMethod(profileId, accountId, f.size)
            } catch (e: TransferRejected) {
                problems += "${f.name}: ${e.message}"
            }
        }
        val total = files.sumOf { it.size.coerceAtLeast(0) }
        val conn = connection()
        val limitBytes = settings().confirmMobileDataAboveMb.toLong() * 1024 * 1024
        _uiState.update {
            it.copy(uploadPlan = UploadPlan(files, methods, problems, total, needsMobileConfirm = conn.metered && total > limitBytes))
        }
    }

    fun cancelUpload() = _uiState.update { it.copy(uploadPlan = null) }

    fun confirmUpload(wifiOnly: Boolean) {
        val plan = _uiState.value.uploadPlan ?: return
        _uiState.update { it.copy(uploadPlan = null) }
        viewModelScope.launch {
            try {
                val ok = plan.files.filter { f -> plan.problems.none { it.startsWith("${f.name}:") } }
                transfers.enqueueUploads(profileId, accountId, bucket, jurisdiction, _uiState.value.prefix, ok, wifiOnly)
                _uiState.update { it.copy(message = "${ok.size} upload${if (ok.size == 1) "" else "s"} queued. Track them in Transfers.") }
            } catch (e: TransferRejected) {
                _uiState.update { it.copy(message = e.message) }
            }
        }
    }

    fun download(obj: R2Object, destination: Uri) {
        viewModelScope.launch {
            transfers.enqueueDownload(profileId, accountId, bucket, jurisdiction, obj.key, obj.sizeBytes, obj.httpMetadata?.contentType, destination, settings().transfersWifiOnly)
            _uiState.update { it.copy(message = "Download queued") }
        }
    }

    fun preview(obj: R2Object) {
        previewJob?.cancel()
        val type = obj.httpMetadata?.contentType.orEmpty().lowercase()
        val name = obj.key.lowercase()
        val isText = type.startsWith("text/") || type.contains("json") || type.contains("xml") || type.contains("javascript") ||
            listOf(".txt", ".json", ".md", ".csv", ".xml", ".log", ".yaml", ".yml", ".toml", ".js", ".ts", ".css", ".html").any(name::endsWith)
        val isImage = type.startsWith("image/") && !type.contains("svg") || listOf(".png", ".jpg", ".jpeg", ".webp", ".gif", ".bmp").any(name::endsWith)
        when {
            isText -> {
                _uiState.update { it.copy(preview = Preview.Loading(obj.key)) }
                previewJob = viewModelScope.launch {
                    when (val r = repository.readPrefix(accountId, bucket, obj.key, jurisdiction, TEXT_PREVIEW_BYTES)) {
                        // HTML is shown as source text, never rendered (spec 37).
                        is ApiResult.Success -> _uiState.update { it.copy(preview = Preview.Text(obj.key, r.data.toString(Charsets.UTF_8), obj.sizeBytes > TEXT_PREVIEW_BYTES)) }
                        is ApiResult.Failure -> _uiState.update { it.copy(preview = Preview.Unsupported(obj.key, r.message)) }
                    }
                }
            }
            isImage && obj.sizeBytes <= IMAGE_PREVIEW_MAX -> {
                _uiState.update { it.copy(preview = Preview.Loading(obj.key)) }
                previewJob = viewModelScope.launch { _uiState.update { it.copy(preview = loadImage(obj)) } }
            }
            isImage -> _uiState.update { it.copy(preview = Preview.Unsupported(obj.key, "Image is larger than 25 MB. Download it to view.")) }
            else -> _uiState.update { it.copy(preview = Preview.Unsupported(obj.key, "No in-app preview for this type. Download it and open it with another app.")) }
        }
    }

    /** Streams to an app-private temp file, decodes downsampled, then deletes the file (spec 189). */
    private suspend fun loadImage(obj: R2Object): Preview = withContext(Dispatchers.IO) {
        val temp = java.io.File.createTempFile("preview", ".img", cacheDir)
        try {
            repository.openObject(accountId, bucket, obj.key, jurisdiction).stream.use { input -> temp.outputStream().use { input.copyTo(it) } }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(temp.path, bounds)
            var sample = 1
            while (bounds.outWidth / sample > 2048 || bounds.outHeight / sample > 2048) sample *= 2
            val bitmap = BitmapFactory.decodeFile(temp.path, BitmapFactory.Options().apply { inSampleSize = sample })
            if (bitmap == null) Preview.Unsupported(obj.key, "This image format could not be decoded.") else Preview.Image(obj.key, bitmap)
        } catch (e: IOException) {
            Preview.Unsupported(obj.key, "Could not load the image: ${e.message}")
        } finally {
            temp.delete()
        }
    }

    fun closePreview() { previewJob?.cancel(); _uiState.update { it.copy(preview = null) } }

    fun saveCredentials(accessKeyId: String, secret: String, jurisdictionOverride: String?) {
        if (accessKeyId.isBlank() || secret.isBlank()) { _uiState.update { it.copy(message = "Enter both the Access Key ID and the Secret Access Key") }; return }
        val creds = R2S3Credentials(accessKeyId.trim(), secret.trim(), jurisdictionOverride ?: jurisdiction)
        viewModelScope.launch {
            val status = try { s3.headBucket(creds, accountId, bucket) } catch (e: IOException) { -1 }
            when (status) {
                in 200..299 -> {
                    credentials.put(profileId, accountId, creds)
                    _uiState.update { it.copy(hasS3Credentials = true, message = "R2 S3 credentials verified and saved on this device") }
                }
                -1 -> _uiState.update { it.copy(message = "Could not reach R2. Credentials were not saved.") }
                401, 403 -> _uiState.update { it.copy(message = "R2 rejected these credentials for $bucket (HTTP $status). Not saved.") }
                else -> _uiState.update { it.copy(message = "R2 answered HTTP $status. Credentials were not saved.") }
            }
        }
    }

    fun removeCredentials() {
        credentials.remove(profileId, accountId)
        _uiState.update { it.copy(hasS3Credentials = false, message = "R2 S3 credentials removed from this device") }
    }

    companion object {
        const val TEXT_PREVIEW_BYTES = 512 * 1024
        const val IMAGE_PREVIEW_MAX = 25L * 1024 * 1024
    }
}

package dev.cfmobile.app.ui.stream

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cfmobile.app.core.errors.ErrorClassifier
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.UploadPayload
import dev.cfmobile.app.data.remote.dto.StreamVideo
import dev.cfmobile.app.data.repository.StreamRepository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class StreamUiState(
    val videos: UiState<List<StreamVideo>> = UiState.Loading,
    val isRefreshing: Boolean = false,
    val deletingId: String? = null,
    val isUploading: Boolean = false,
    val uploadError: String? = null,
    val uploadedName: String? = null
)

/** Cloudflare's basic upload takes the whole file in one request and rejects anything past
 *  200 MB; the resumable protocol that handles bigger files isn't implemented, so the limit is
 *  checked here rather than after a long upload fails. */
const val STREAM_BASIC_UPLOAD_LIMIT_BYTES = 200L * 1024 * 1024

fun streamUploadSizeError(sizeBytes: Long): String? =
    if (sizeBytes > STREAM_BASIC_UPLOAD_LIMIT_BYTES) {
        "That video is over the 200 MB limit for this app's upload - Cloudflare needs a resumable upload above that, which isn't implemented."
    } else {
        null
    }

/** Videos carry their display name in free-form metadata, so fall back to the uid rather than
 *  showing an untitled row. */
fun streamVideoTitle(video: StreamVideo): String =
    (video.meta?.get("name") as? String)?.takeIf { it.isNotBlank() } ?: video.uid

/** Formats a duration in seconds as m:ss (or h:mm:ss past an hour). Cloudflare reports -1 for
 *  videos it hasn't finished processing, which shouldn't render as a negative timestamp. */
fun formatDuration(seconds: Double?): String? {
    if (seconds == null || seconds < 0) return null
    val total = seconds.roundToInt()
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val secs = total % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, secs)
    } else {
        "%d:%02d".format(minutes, secs)
    }
}

class StreamViewModel(
    private val accountId: String,
    private val repository: StreamRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(StreamUiState())
    val uiState: StateFlow<StreamUiState> = _uiState.asStateFlow()

    init {
        load(isRefresh = false)
    }

    fun refresh() = load(isRefresh = true)

    private fun load(isRefresh: Boolean) {
        _uiState.update { if (isRefresh) it.copy(isRefreshing = true) else it.copy(videos = UiState.Loading) }
        viewModelScope.launch {
            when (val result = repository.listVideos(accountId)) {
                is ApiResult.Success -> _uiState.update { it.copy(videos = UiState.Data(result.data), isRefreshing = false) }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(videos = UiState.Error(ErrorClassifier.classify(result)), isRefreshing = false)
                }
            }
        }
    }

    fun delete(video: StreamVideo) {
        _uiState.update { it.copy(deletingId = video.uid) }
        viewModelScope.launch {
            repository.deleteVideo(accountId, video.uid)
            _uiState.update { it.copy(deletingId = null) }
            load(isRefresh = true)
        }
    }

    /**
     * Uploads a video the user picked. Cloudflare processes the file after it lands, so the
     * new row shows as still encoding rather than ready to play.
     */
    fun upload(payload: UploadPayload?) {
        if (payload == null) {
            _uiState.update { it.copy(uploadError = "Couldn't read the selected video") }
            return
        }
        val sizeError = payload.body.contentLength().takeIf { it > 0 }?.let(::streamUploadSizeError)
        if (sizeError != null) {
            _uiState.update { it.copy(uploadError = sizeError) }
            return
        }
        _uiState.update { it.copy(isUploading = true, uploadError = null, uploadedName = null) }
        viewModelScope.launch {
            when (val result = repository.uploadVideo(accountId, payload)) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(isUploading = false, uploadedName = payload.fileName) }
                    load(isRefresh = true)
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(isUploading = false, uploadError = result.message)
                }
            }
        }
    }

    fun dismissUploadStatus() = _uiState.update { it.copy(uploadError = null, uploadedName = null) }
}
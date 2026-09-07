package dev.cfmobile.app.ui.stream

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cfmobile.app.core.errors.ErrorClassifier
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.UploadPayload
import dev.cfmobile.app.data.remote.dto.StreamCaption
import dev.cfmobile.app.data.remote.dto.StreamLiveInput
import dev.cfmobile.app.data.remote.dto.StreamSigningKey
import dev.cfmobile.app.data.remote.dto.StreamVideo
import dev.cfmobile.app.data.remote.dto.StreamWatermark
import dev.cfmobile.app.data.repository.StreamRepository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

enum class StreamTab(val label: String) {
    VIDEOS("Videos"),
    LIVE("Live"),
    WATERMARKS("Watermarks"),
    KEYS("Keys")
}

data class LiveInputFormState(
    val name: String = "",
    val record: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null
)

/** One live input's push endpoints, loaded on demand. The stream key is a credential, so it
 *  is held only while this sheet is open and never written anywhere. */
data class LiveInputDetail(
    val input: StreamLiveInput,
    val isLoading: Boolean = true,
    val error: String? = null
)

/** The caption tracks on one video. */
data class CaptionsState(
    val video: StreamVideo,
    val captions: List<StreamCaption> = emptyList(),
    val isLoading: Boolean = true,
    val deletingLanguage: String? = null,
    val error: String? = null
)

data class StreamUiState(
    val tab: StreamTab = StreamTab.VIDEOS,
    val videos: UiState<List<StreamVideo>> = UiState.Loading,
    val liveInputs: UiState<List<StreamLiveInput>> = UiState.Loading,
    val watermarks: UiState<List<StreamWatermark>> = UiState.Loading,
    val signingKeys: UiState<List<StreamSigningKey>> = UiState.Loading,
    val isRefreshing: Boolean = false,
    val deletingId: String? = null,
    val isUploading: Boolean = false,
    val uploadError: String? = null,
    val uploadedName: String? = null,
    val liveInputForm: LiveInputFormState? = null,
    val liveInputDetail: LiveInputDetail? = null,
    val captions: CaptionsState? = null,
    val error: String? = null
)

/** A live input's display name lives in the same free-form metadata a video's does. */
fun liveInputName(input: StreamLiveInput): String =
    input.meta?.get("name")?.takeIf { it.isNotBlank() } ?: input.uid

/** "live · recording on" - whether it is broadcasting and whether that is being kept. */
fun liveInputSummary(input: StreamLiveInput): String = listOfNotNull(
    input.status?.current?.state ?: "idle",
    if (input.recording?.mode == "automatic") "recording on" else "recording off"
).joinToString(" · ")

/** "top-right · 20% opacity" - how the watermark will sit on the frame. */
fun watermarkSummary(watermark: StreamWatermark): String = listOfNotNull(
    watermark.position,
    watermark.opacity?.let { "${(it * 100).roundToInt()}% opacity" },
    watermark.scale?.let { "${(it * 100).roundToInt()}% scale" }
).joinToString(" · ")

/** "English · generated" - what a caption track is and where it came from. */
fun captionSummary(caption: StreamCaption): String = listOfNotNull(
    caption.status,
    if (caption.generated == true) "auto-generated" else "uploaded"
).joinToString(" · ")

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

    fun selectTab(tab: StreamTab) = _uiState.update { it.copy(tab = tab) }

    /** All four lists in one coroutine, as elsewhere: independent launches would race at the
     *  HTTP dispatcher with nothing gained. */
    private fun load(isRefresh: Boolean) {
        _uiState.update {
            if (isRefresh) it.copy(isRefreshing = true)
            else it.copy(
                videos = UiState.Loading,
                liveInputs = UiState.Loading,
                watermarks = UiState.Loading,
                signingKeys = UiState.Loading
            )
        }
        viewModelScope.launch {
            when (val result = repository.listVideos(accountId)) {
                is ApiResult.Success -> _uiState.update { it.copy(videos = UiState.Data(result.data)) }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(videos = UiState.Error(ErrorClassifier.classify(result)))
                }
            }
            when (val result = repository.listLiveInputs(accountId)) {
                is ApiResult.Success -> _uiState.update { it.copy(liveInputs = UiState.Data(result.data)) }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(liveInputs = UiState.Error(ErrorClassifier.classify(result)))
                }
            }
            when (val result = repository.listWatermarks(accountId)) {
                is ApiResult.Success -> _uiState.update { it.copy(watermarks = UiState.Data(result.data)) }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(watermarks = UiState.Error(ErrorClassifier.classify(result)))
                }
            }
            when (val result = repository.listSigningKeys(accountId)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(signingKeys = UiState.Data(result.data), isRefreshing = false)
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(signingKeys = UiState.Error(ErrorClassifier.classify(result)), isRefreshing = false)
                }
            }
        }
    }

    // ---- Live inputs ----

    fun openLiveInputForm() = _uiState.update { it.copy(liveInputForm = LiveInputFormState()) }

    fun closeLiveInputForm() = _uiState.update { it.copy(liveInputForm = null) }

    fun updateLiveInputForm(transform: (LiveInputFormState) -> LiveInputFormState) =
        _uiState.update { state -> state.liveInputForm?.let { state.copy(liveInputForm = transform(it)) } ?: state }

    fun saveLiveInput() {
        val form = _uiState.value.liveInputForm ?: return
        if (form.name.isBlank()) {
            updateLiveInputForm { it.copy(error = "A name is required") }
            return
        }
        updateLiveInputForm { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.createLiveInput(accountId, form.name.trim(), form.record)) {
                is ApiResult.Success -> {
                    // The create response already carries the push endpoints, so the sheet
                    // opens straight onto them rather than making a second call.
                    _uiState.update {
                        it.copy(
                            liveInputForm = null,
                            liveInputDetail = LiveInputDetail(input = result.data, isLoading = false)
                        )
                    }
                    load(isRefresh = true)
                }
                is ApiResult.Failure -> updateLiveInputForm { it.copy(isSaving = false, error = result.message) }
            }
        }
    }

    /** The list omits the stream keys, so opening an input fetches it on its own. */
    fun openLiveInput(input: StreamLiveInput) {
        _uiState.update { it.copy(liveInputDetail = LiveInputDetail(input = input)) }
        viewModelScope.launch {
            when (val result = repository.getLiveInput(accountId, input.uid)) {
                is ApiResult.Success -> _uiState.update { state ->
                    if (state.liveInputDetail?.input?.uid != input.uid) return@update state
                    state.copy(liveInputDetail = LiveInputDetail(input = result.data, isLoading = false))
                }
                is ApiResult.Failure -> _uiState.update { state ->
                    if (state.liveInputDetail?.input?.uid != input.uid) return@update state
                    state.copy(liveInputDetail = state.liveInputDetail.copy(isLoading = false, error = result.message))
                }
            }
        }
    }

    /** Dropping the detail is what clears the stream key from memory. */
    fun closeLiveInput() = _uiState.update { it.copy(liveInputDetail = null) }

    fun deleteLiveInput(input: StreamLiveInput) = deleteById(input.uid) {
        repository.deleteLiveInput(accountId, input.uid)
    }

    fun deleteWatermark(watermark: StreamWatermark) = deleteById(watermark.uid) {
        repository.deleteWatermark(accountId, watermark.uid)
    }

    fun deleteSigningKey(key: StreamSigningKey) = deleteById(key.id) {
        repository.deleteSigningKey(accountId, key.id)
    }

    private fun deleteById(id: String, request: suspend () -> ApiResult<Unit>) {
        _uiState.update { it.copy(deletingId = id, error = null) }
        viewModelScope.launch {
            when (val result = request()) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(deletingId = null) }
                    load(isRefresh = true)
                }
                is ApiResult.Failure -> _uiState.update { it.copy(deletingId = null, error = result.message) }
            }
        }
    }

    // ---- Captions ----

    fun openCaptions(video: StreamVideo) {
        _uiState.update { it.copy(captions = CaptionsState(video = video)) }
        viewModelScope.launch {
            when (val result = repository.listCaptions(accountId, video.uid)) {
                is ApiResult.Success -> _uiState.update { state ->
                    if (state.captions?.video?.uid != video.uid) return@update state
                    state.copy(captions = state.captions.copy(captions = result.data, isLoading = false))
                }
                is ApiResult.Failure -> _uiState.update { state ->
                    if (state.captions?.video?.uid != video.uid) return@update state
                    state.copy(captions = state.captions.copy(isLoading = false, error = result.message))
                }
            }
        }
    }

    fun closeCaptions() = _uiState.update { it.copy(captions = null) }

    fun deleteCaption(caption: StreamCaption) {
        val captions = _uiState.value.captions ?: return
        _uiState.update { it.copy(captions = captions.copy(deletingLanguage = caption.language, error = null)) }
        viewModelScope.launch {
            when (val result = repository.deleteCaption(accountId, captions.video.uid, caption.language)) {
                is ApiResult.Success -> _uiState.update { state ->
                    val current = state.captions ?: return@update state
                    state.copy(
                        captions = current.copy(
                            captions = current.captions.filterNot { it.language == caption.language },
                            deletingLanguage = null
                        )
                    )
                }
                is ApiResult.Failure -> _uiState.update { state ->
                    val current = state.captions ?: return@update state
                    state.copy(captions = current.copy(deletingLanguage = null, error = result.message))
                }
            }
        }
    }

    fun dismissError() = _uiState.update { it.copy(error = null) }

    fun delete(video: StreamVideo) = deleteById(video.uid) { repository.deleteVideo(accountId, video.uid) }

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
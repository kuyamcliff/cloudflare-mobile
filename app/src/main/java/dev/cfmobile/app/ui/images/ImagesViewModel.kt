package dev.cfmobile.app.ui.images

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cfmobile.app.core.errors.ErrorClassifier
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.UploadPayload
import dev.cfmobile.app.data.remote.dto.CfImage
import dev.cfmobile.app.data.remote.dto.ImagesStats
import dev.cfmobile.app.data.repository.ImagesRepository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ImagesUiState(
    val images: UiState<List<CfImage>> = UiState.Loading,
    val isRefreshing: Boolean = false,
    val stats: ImagesStats? = null,
    val deletingId: String? = null,
    val isUploading: Boolean = false,
    val uploadError: String? = null,
    val uploadedName: String? = null
)

/** "1,204 of 100,000 stored" - null when the account didn't report a quota. */
fun imagesUsageLabel(stats: ImagesStats?): String? {
    val count = stats?.count ?: return null
    val current = count.current
    val allowed = count.allowed
    return if (allowed != null) "%,d of %,d images stored".format(current, allowed) else "%,d images stored".format(current)
}

class ImagesViewModel(
    private val accountId: String,
    private val repository: ImagesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImagesUiState())
    val uiState: StateFlow<ImagesUiState> = _uiState.asStateFlow()

    init {
        load(isRefresh = false)
    }

    fun refresh() = load(isRefresh = true)

    private fun load(isRefresh: Boolean) {
        _uiState.update { if (isRefresh) it.copy(isRefreshing = true) else it.copy(images = UiState.Loading) }
        viewModelScope.launch {
            // Sequential rather than two parallel launches: the two calls share one connection
            // and their arrival order would otherwise be a race (see LoadBalancingViewModel).
            when (val result = repository.listImages(accountId)) {
                is ApiResult.Success -> _uiState.update { it.copy(images = UiState.Data(result.data), isRefreshing = false) }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(images = UiState.Error(ErrorClassifier.classify(result)), isRefreshing = false)
                }
            }
            // Quota is supplementary: if it fails, the list is still worth showing.
            when (val stats = repository.getStats(accountId)) {
                is ApiResult.Success -> _uiState.update { it.copy(stats = stats.data) }
                is ApiResult.Failure -> Unit
            }
        }
    }

    fun delete(image: CfImage) {
        _uiState.update { it.copy(deletingId = image.id) }
        viewModelScope.launch {
            repository.deleteImage(accountId, image.id)
            _uiState.update { it.copy(deletingId = null) }
            load(isRefresh = true)
        }
    }

    /**
     * Uploads a picture the user picked. The payload streams from the content provider, so a
     * large image never sits in memory whole; a null payload means the picked file couldn't be
     * opened at all, which is reported rather than sent as an empty file.
     */
    fun upload(payload: UploadPayload?) {
        if (payload == null) {
            _uiState.update { it.copy(uploadError = "Couldn't read the selected image") }
            return
        }
        _uiState.update { it.copy(isUploading = true, uploadError = null, uploadedName = null) }
        viewModelScope.launch {
            when (val result = repository.uploadImage(accountId, payload)) {
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
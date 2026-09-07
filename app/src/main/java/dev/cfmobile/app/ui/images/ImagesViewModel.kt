package dev.cfmobile.app.ui.images

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cfmobile.app.core.errors.ErrorClassifier
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.UploadPayload
import dev.cfmobile.app.data.remote.dto.CfImage
import dev.cfmobile.app.data.remote.dto.ImageSigningKey
import dev.cfmobile.app.data.remote.dto.ImageVariant
import dev.cfmobile.app.data.remote.dto.ImageVariantOptions
import dev.cfmobile.app.data.remote.dto.ImagesStats
import dev.cfmobile.app.data.repository.ImagesRepository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ImagesTab(val label: String) {
    IMAGES("Images"),
    VARIANTS("Variants"),
    KEYS("Keys")
}

/** How a variant resizes an image. These are Cloudflare's own fit modes. */
enum class VariantFit(val value: String, val label: String, val description: String) {
    SCALE_DOWN("scale-down", "Scale down", "Shrink to fit the box; never enlarge"),
    CONTAIN("contain", "Contain", "Fit inside the box, keeping the whole image"),
    COVER("cover", "Cover", "Fill the box, cropping the overflow"),
    CROP("crop", "Crop", "Cut to exactly this size, never enlarging"),
    PAD("pad", "Pad", "Fit inside the box and pad the rest")
}

fun variantFitFromValue(value: String?): VariantFit =
    VariantFit.entries.firstOrNull { it.value == value } ?: VariantFit.SCALE_DOWN

data class VariantFormState(
    val id: String = "",
    val width: String = "",
    val height: String = "",
    val fit: VariantFit = VariantFit.SCALE_DOWN,
    val keepMetadata: Boolean = false,
    val neverRequireSignedUrls: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null
)

data class ImagesUiState(
    val tab: ImagesTab = ImagesTab.IMAGES,
    val images: UiState<List<CfImage>> = UiState.Loading,
    val variants: UiState<List<ImageVariant>> = UiState.Loading,
    val signingKeys: UiState<List<ImageSigningKey>> = UiState.Loading,
    val isRefreshing: Boolean = false,
    val stats: ImagesStats? = null,
    val deletingId: String? = null,
    val isUploading: Boolean = false,
    val uploadError: String? = null,
    val uploadedName: String? = null,
    val variantForm: VariantFormState? = null,
    val error: String? = null
)

/** A variant id becomes a path segment in every delivery URL, so it is restricted like one. */
private val VARIANT_ID_REGEX = Regex("^[A-Za-z0-9][A-Za-z0-9_-]*$")

fun validateVariantForm(form: VariantFormState): String? {
    if (form.id.isBlank()) return "A variant name is required"
    if (!form.id.trim().matches(VARIANT_ID_REGEX)) {
        return "Use letters, digits, hyphens and underscores - the name becomes part of every delivery URL"
    }
    val width = form.width.trim().toIntOrNull()
    if (width == null || width <= 0) return "Width must be a positive number of pixels"
    val height = form.height.trim().toIntOrNull()
    if (height == null || height <= 0) return "Height must be a positive number of pixels"
    return null
}

fun buildVariantOptions(form: VariantFormState) = ImageVariantOptions(
    fit = form.fit.value,
    width = form.width.trim().toInt(),
    height = form.height.trim().toInt(),
    // Cloudflare's own values: "keep" preserves EXIF, "none" strips it.
    metadata = if (form.keepMetadata) "keep" else "none"
)

/** "800×600 · cover · signed URLs required" - what the variant delivers. */
fun variantSummary(variant: ImageVariant): String {
    val options = variant.options
    return listOfNotNull(
        options?.let { "${it.width}×${it.height}" },
        options?.fit,
        if (variant.neverRequireSignedUrls == true) "public" else "signed URLs required"
    ).joinToString(" · ")
}

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

    fun selectTab(tab: ImagesTab) = _uiState.update { it.copy(tab = tab) }

    private fun load(isRefresh: Boolean) {
        _uiState.update {
            if (isRefresh) it.copy(isRefreshing = true)
            else it.copy(images = UiState.Loading, variants = UiState.Loading, signingKeys = UiState.Loading)
        }
        viewModelScope.launch {
            // Sequential rather than two parallel launches: the two calls share one connection
            // and their arrival order would otherwise be a race (see LoadBalancingViewModel).
            when (val result = repository.listImages(accountId)) {
                is ApiResult.Success -> _uiState.update { it.copy(images = UiState.Data(result.data)) }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(images = UiState.Error(ErrorClassifier.classify(result)))
                }
            }
            // Quota is supplementary: if it fails, the list is still worth showing.
            when (val stats = repository.getStats(accountId)) {
                is ApiResult.Success -> _uiState.update { it.copy(stats = stats.data) }
                is ApiResult.Failure -> Unit
            }
            when (val result = repository.listVariants(accountId)) {
                is ApiResult.Success -> _uiState.update { it.copy(variants = UiState.Data(result.data)) }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(variants = UiState.Error(ErrorClassifier.classify(result)))
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

    // ---- Variants ----

    fun openVariantForm() = _uiState.update { it.copy(variantForm = VariantFormState()) }

    fun closeVariantForm() = _uiState.update { it.copy(variantForm = null) }

    fun updateVariantForm(transform: (VariantFormState) -> VariantFormState) =
        _uiState.update { state -> state.variantForm?.let { state.copy(variantForm = transform(it)) } ?: state }

    fun saveVariant() {
        val form = _uiState.value.variantForm ?: return
        val validationError = validateVariantForm(form)
        if (validationError != null) {
            updateVariantForm { it.copy(error = validationError) }
            return
        }
        updateVariantForm { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            val result = repository.createVariant(
                accountId = accountId,
                id = form.id.trim(),
                options = buildVariantOptions(form),
                neverRequireSignedUrls = form.neverRequireSignedUrls
            )
            when (result) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(variantForm = null) }
                    load(isRefresh = true)
                }
                is ApiResult.Failure -> updateVariantForm { it.copy(isSaving = false, error = result.message) }
            }
        }
    }

    fun deleteVariant(variant: ImageVariant) {
        _uiState.update { it.copy(deletingId = variant.id, error = null) }
        viewModelScope.launch {
            when (val result = repository.deleteVariant(accountId, variant.id)) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(deletingId = null) }
                    load(isRefresh = true)
                }
                is ApiResult.Failure -> _uiState.update { it.copy(deletingId = null, error = result.message) }
            }
        }
    }

    fun dismissError() = _uiState.update { it.copy(error = null) }

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
package dev.cfmobile.app.ui.vectorize

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cfmobile.app.core.errors.ErrorClassifier
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.dto.VectorizeIndex
import dev.cfmobile.app.data.repository.VectorizeRepository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val INDEX_NAME_REGEX = Regex("^[a-z0-9][a-z0-9-]{0,62}$")

/** Cloudflare's supported distance metrics. Offered as a fixed list because a typo here is
 *  only discovered when the index is already created and can't be edited. */
enum class VectorizeMetric(val apiValue: String, val label: String) {
    COSINE("cosine", "Cosine"),
    EUCLIDEAN("euclidean", "Euclidean"),
    DOT_PRODUCT("dot-product", "Dot product")
}

data class VectorizeFormState(
    val name: String = "",
    val dimensions: String = "768",
    val metric: VectorizeMetric = VectorizeMetric.COSINE,
    val description: String = "",
    val isSaving: Boolean = false,
    val error: String? = null
)

data class VectorizeUiState(
    val indexes: UiState<List<VectorizeIndex>> = UiState.Loading,
    val isRefreshing: Boolean = false,
    val form: VectorizeFormState? = null,
    val deletingName: String? = null
)

fun validateVectorizeForm(form: VectorizeFormState): String? {
    val dimensions = form.dimensions.trim().toIntOrNull()
    return when {
        form.name.isBlank() -> "Index name is required"
        !form.name.trim().matches(INDEX_NAME_REGEX) -> "Use up to 63 lowercase letters, numbers, or hyphens"
        form.dimensions.isBlank() -> "Dimensions are required"
        dimensions == null -> "Dimensions must be a whole number"
        dimensions !in 1..1536 -> "Dimensions must be between 1 and 1536"
        else -> null
    }
}

class VectorizeViewModel(
    private val accountId: String,
    private val repository: VectorizeRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(VectorizeUiState())
    val uiState: StateFlow<VectorizeUiState> = _uiState.asStateFlow()

    init {
        load(isRefresh = false)
    }

    fun refresh() = load(isRefresh = true)

    private fun load(isRefresh: Boolean) {
        _uiState.update { if (isRefresh) it.copy(isRefreshing = true) else it.copy(indexes = UiState.Loading) }
        viewModelScope.launch {
            when (val result = repository.listIndexes(accountId)) {
                is ApiResult.Success -> _uiState.update { it.copy(indexes = UiState.Data(result.data), isRefreshing = false) }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(indexes = UiState.Error(ErrorClassifier.classify(result)), isRefreshing = false)
                }
            }
        }
    }

    fun openForm() = _uiState.update { it.copy(form = VectorizeFormState()) }
    fun closeForm() = _uiState.update { it.copy(form = null) }

    fun updateForm(transform: (VectorizeFormState) -> VectorizeFormState) =
        _uiState.update { state -> state.form?.let { state.copy(form = transform(it)) } ?: state }

    fun save() {
        val form = _uiState.value.form ?: return
        val validationError = validateVectorizeForm(form)
        if (validationError != null) {
            updateForm { it.copy(error = validationError) }
            return
        }
        updateForm { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            val result = repository.createIndex(
                accountId = accountId,
                name = form.name.trim(),
                dimensions = form.dimensions.trim().toInt(),
                metric = form.metric.apiValue,
                description = form.description
            )
            when (result) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(form = null) }
                    load(isRefresh = true)
                }
                is ApiResult.Failure -> updateForm { it.copy(isSaving = false, error = result.message) }
            }
        }
    }

    fun delete(index: VectorizeIndex) {
        _uiState.update { it.copy(deletingName = index.name) }
        viewModelScope.launch {
            repository.deleteIndex(accountId, index.name)
            _uiState.update { it.copy(deletingName = null) }
            load(isRefresh = true)
        }
    }
}

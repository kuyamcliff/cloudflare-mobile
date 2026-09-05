package dev.cfmobile.app.ui.r2

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cfmobile.app.core.errors.ErrorClassifier
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.dto.R2Bucket
import dev.cfmobile.app.data.repository.R2Repository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val BUCKET_NAME_REGEX = Regex("^[a-z0-9][a-z0-9-]{1,61}[a-z0-9]$")

data class R2FormState(val name: String = "", val isSaving: Boolean = false, val error: String? = null)

data class R2UiState(
    val buckets: UiState<List<R2Bucket>> = UiState.Loading,
    val form: R2FormState? = null,
    val deletingName: String? = null
)

/** R2 bucket names follow S3's rules - 3 to 63 characters, lowercase letters/digits/hyphens,
 *  and can't start or end with a hyphen. Checking this client-side means a doomed request
 *  never leaves the device. */
fun validateBucketName(name: String): String? = when {
    name.isBlank() -> "Bucket name is required"
    !name.matches(BUCKET_NAME_REGEX) -> "Use 3-63 lowercase letters, numbers, or hyphens (can't start or end with a hyphen)"
    else -> null
}

class R2ViewModel(
    private val accountId: String,
    private val repository: R2Repository
) : ViewModel() {

    private val _uiState = MutableStateFlow(R2UiState())
    val uiState: StateFlow<R2UiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _uiState.update { it.copy(buckets = UiState.Loading) }
        viewModelScope.launch {
            when (val result = repository.listBuckets(accountId)) {
                is ApiResult.Success -> _uiState.update { it.copy(buckets = UiState.Data(result.data)) }
                is ApiResult.Failure -> _uiState.update { it.copy(buckets = UiState.Error(ErrorClassifier.classify(result))) }
            }
        }
    }

    fun openForm() = _uiState.update { it.copy(form = R2FormState()) }
    fun closeForm() = _uiState.update { it.copy(form = null) }

    fun updateForm(transform: (R2FormState) -> R2FormState) =
        _uiState.update { state -> state.form?.let { state.copy(form = transform(it)) } ?: state }

    fun save() {
        val form = _uiState.value.form ?: return
        val validationError = validateBucketName(form.name)
        if (validationError != null) {
            updateForm { it.copy(error = validationError) }
            return
        }
        updateForm { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.createBucket(accountId, form.name.trim())) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(form = null) }
                    refresh()
                }
                is ApiResult.Failure -> updateForm { it.copy(isSaving = false, error = result.message) }
            }
        }
    }

    fun delete(bucket: R2Bucket) {
        _uiState.update { it.copy(deletingName = bucket.name) }
        viewModelScope.launch {
            repository.deleteBucket(accountId, bucket.name)
            _uiState.update { it.copy(deletingName = null) }
            refresh()
        }
    }
}

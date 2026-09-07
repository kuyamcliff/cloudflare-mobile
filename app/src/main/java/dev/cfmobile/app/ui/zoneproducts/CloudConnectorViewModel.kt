package dev.cfmobile.app.ui.zoneproducts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cfmobile.app.core.errors.ErrorClassifier
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.dto.CloudConnectorParameters
import dev.cfmobile.app.data.remote.dto.CloudConnectorRule
import dev.cfmobile.app.data.repository.CloudConnectorRepository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The object-storage providers Cloud Connector can route to. */
enum class CloudProvider(val apiValue: String, val label: String, val hostPlaceholder: String) {
    R2("cloudflare_r2", "Cloudflare R2", "bucket.account.r2.cloudflarestorage.com"),
    AWS_S3("aws_s3", "Amazon S3", "bucket.s3.amazonaws.com"),
    AZURE_STORAGE("azure_storage", "Azure Storage", "account.blob.core.windows.net"),
    GCP_STORAGE("gcp_storage", "Google Cloud Storage", "storage.googleapis.com")
}

data class CloudConnectorFormState(
    val editingId: String? = null,
    val expression: String = "true",
    val description: String = "",
    val provider: CloudProvider = CloudProvider.R2,
    val host: String = "",
    val enabled: Boolean = true,
    val isSaving: Boolean = false,
    val error: String? = null
)

data class CloudConnectorUiState(
    val rules: UiState<List<CloudConnectorRule>> = UiState.Loading,
    val isRefreshing: Boolean = false,
    val form: CloudConnectorFormState? = null,
    val deletingId: String? = null,
    val error: String? = null
)

private val HOST_REGEX = Regex("^[a-zA-Z0-9]([a-zA-Z0-9-]*[a-zA-Z0-9])?(\\.[a-zA-Z0-9]([a-zA-Z0-9-]*[a-zA-Z0-9])?)+$")

fun validateCloudConnectorForm(form: CloudConnectorFormState): String? = when {
    form.expression.isBlank() -> "Expression is required"
    form.host.isBlank() -> "Bucket hostname is required"
    // A scheme or path here silently fails at the edge, so it's rejected up front.
    form.host.contains("/") -> "Enter just the hostname - no scheme and no path"
    !form.host.trim().matches(HOST_REGEX) -> "Enter a valid hostname, e.g. bucket.s3.amazonaws.com"
    else -> null
}

fun providerLabel(apiValue: String?): String =
    CloudProvider.entries.firstOrNull { it.apiValue == apiValue }?.label ?: apiValue.orEmpty()

/** "Cloudflare R2 · bucket.example.r2.cloudflarestorage.com" */
fun cloudConnectorSummary(rule: CloudConnectorRule): String =
    listOfNotNull(providerLabel(rule.provider).ifBlank { null }, rule.parameters?.host?.takeIf { it.isNotBlank() })
        .joinToString(" · ")
        .ifBlank { rule.expression }

fun cloudConnectorFormOf(rule: CloudConnectorRule): CloudConnectorFormState = CloudConnectorFormState(
    editingId = rule.id,
    expression = rule.expression,
    description = rule.description.orEmpty(),
    provider = CloudProvider.entries.firstOrNull { it.apiValue == rule.provider } ?: CloudProvider.R2,
    host = rule.parameters?.host.orEmpty(),
    enabled = rule.enabled
)

class CloudConnectorViewModel(
    private val zoneId: String,
    private val repository: CloudConnectorRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CloudConnectorUiState())
    val uiState: StateFlow<CloudConnectorUiState> = _uiState.asStateFlow()

    init {
        load(isRefresh = false)
    }

    fun refresh() = load(isRefresh = true)

    private fun load(isRefresh: Boolean) {
        _uiState.update { if (isRefresh) it.copy(isRefreshing = true) else it.copy(rules = UiState.Loading) }
        viewModelScope.launch {
            when (val result = repository.listRules(zoneId)) {
                is ApiResult.Success -> _uiState.update { it.copy(rules = UiState.Data(result.data), isRefreshing = false) }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(rules = UiState.Error(ErrorClassifier.classify(result)), isRefreshing = false)
                }
            }
        }
    }

    fun openCreateForm() = _uiState.update { it.copy(form = CloudConnectorFormState()) }

    fun openEditForm(rule: CloudConnectorRule) = _uiState.update { it.copy(form = cloudConnectorFormOf(rule)) }

    fun closeForm() = _uiState.update { it.copy(form = null) }

    fun updateForm(transform: (CloudConnectorFormState) -> CloudConnectorFormState) =
        _uiState.update { state -> state.form?.let { state.copy(form = transform(it)) } ?: state }

    /**
     * Cloudflare replaces the zone's whole rule list on write, so a save sends the current list
     * with this rule added or replaced. That also means the response, not the local edit, is
     * what the screen shows afterwards.
     */
    fun save() {
        val form = _uiState.value.form ?: return
        val current = (_uiState.value.rules as? UiState.Data)?.value ?: return
        val validationError = validateCloudConnectorForm(form)
        if (validationError != null) {
            updateForm { it.copy(error = validationError) }
            return
        }
        updateForm { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            val rule = CloudConnectorRule(
                id = form.editingId,
                expression = form.expression.trim(),
                provider = form.provider.apiValue,
                description = form.description.trim().ifBlank { null },
                enabled = form.enabled,
                parameters = CloudConnectorParameters(host = form.host.trim())
            )
            val updated = if (form.editingId != null) {
                current.map { if (it.id == form.editingId) rule else it }
            } else {
                current + rule
            }
            when (val result = repository.putRules(zoneId, updated)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(form = null, rules = UiState.Data(result.data))
                }
                is ApiResult.Failure -> updateForm { it.copy(isSaving = false, error = result.message) }
            }
        }
    }

    fun setEnabled(rule: CloudConnectorRule, enabled: Boolean) {
        val current = (_uiState.value.rules as? UiState.Data)?.value ?: return
        _uiState.update { it.copy(deletingId = rule.id, error = null) }
        viewModelScope.launch {
            val updated = current.map { if (it.id == rule.id) it.copy(enabled = enabled) else it }
            when (val result = repository.putRules(zoneId, updated)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(deletingId = null, rules = UiState.Data(result.data))
                }
                is ApiResult.Failure -> _uiState.update { it.copy(deletingId = null, error = result.message) }
            }
        }
    }

    fun delete(rule: CloudConnectorRule) {
        val current = (_uiState.value.rules as? UiState.Data)?.value ?: return
        _uiState.update { it.copy(deletingId = rule.id, error = null) }
        viewModelScope.launch {
            when (val result = repository.putRules(zoneId, current.filterNot { it.id == rule.id })) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(deletingId = null, rules = UiState.Data(result.data))
                }
                is ApiResult.Failure -> _uiState.update { it.copy(deletingId = null, error = result.message) }
            }
        }
    }

    fun dismissError() = _uiState.update { it.copy(error = null) }
}

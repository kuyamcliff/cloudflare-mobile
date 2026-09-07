package dev.cfmobile.app.ui.healthchecks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cfmobile.app.core.errors.ErrorClassifier
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.dto.HealthCheck
import dev.cfmobile.app.data.repository.HealthChecksRepository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class HealthCheckType(val apiValue: String, val label: String) {
    HTTPS("HTTPS", "HTTPS"),
    HTTP("HTTP", "HTTP"),
    TCP("TCP", "TCP")
}

data class HealthCheckFormState(
    val name: String = "",
    val address: String = "",
    val type: HealthCheckType = HealthCheckType.HTTPS,
    val description: String = "",
    val isSaving: Boolean = false,
    val error: String? = null
)

data class HealthChecksUiState(
    val checks: UiState<List<HealthCheck>> = UiState.Loading,
    val isRefreshing: Boolean = false,
    val form: HealthCheckFormState? = null,
    val deletingId: String? = null
)

fun validateHealthCheckForm(form: HealthCheckFormState): String? = when {
    form.name.isBlank() -> "Check name is required"
    form.address.isBlank() -> "Address is required"
    else -> null
}

/** Cloudflare reports health as "healthy", "unhealthy", or "unknown" before the first probe. */
fun healthStatusTone(status: String?): String = when (status?.lowercase()) {
    "healthy" -> "healthy"
    "unhealthy" -> "unhealthy"
    else -> "unknown"
}

class HealthChecksViewModel(
    private val zoneId: String,
    private val repository: HealthChecksRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HealthChecksUiState())
    val uiState: StateFlow<HealthChecksUiState> = _uiState.asStateFlow()

    init {
        load(isRefresh = false)
    }

    fun refresh() = load(isRefresh = true)

    private fun load(isRefresh: Boolean) {
        _uiState.update { if (isRefresh) it.copy(isRefreshing = true) else it.copy(checks = UiState.Loading) }
        viewModelScope.launch {
            when (val result = repository.listChecks(zoneId)) {
                is ApiResult.Success -> _uiState.update { it.copy(checks = UiState.Data(result.data), isRefreshing = false) }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(checks = UiState.Error(ErrorClassifier.classify(result)), isRefreshing = false)
                }
            }
        }
    }

    fun openForm() = _uiState.update { it.copy(form = HealthCheckFormState()) }
    fun closeForm() = _uiState.update { it.copy(form = null) }

    fun updateForm(transform: (HealthCheckFormState) -> HealthCheckFormState) =
        _uiState.update { state -> state.form?.let { state.copy(form = transform(it)) } ?: state }

    fun save() {
        val form = _uiState.value.form ?: return
        val validationError = validateHealthCheckForm(form)
        if (validationError != null) {
            updateForm { it.copy(error = validationError) }
            return
        }
        updateForm { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            val result = repository.createCheck(
                zoneId = zoneId,
                name = form.name.trim(),
                address = form.address.trim(),
                type = form.type.apiValue,
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

    fun delete(check: HealthCheck) {
        _uiState.update { it.copy(deletingId = check.id) }
        viewModelScope.launch {
            repository.deleteCheck(zoneId, check.id)
            _uiState.update { it.copy(deletingId = null) }
            load(isRefresh = true)
        }
    }
}

package dev.cfmobile.app.ui.workers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cfmobile.app.core.errors.ErrorClassifier
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.dto.CfZone
import dev.cfmobile.app.data.remote.dto.WorkerDomain
import dev.cfmobile.app.data.repository.WorkersRepository
import dev.cfmobile.app.data.repository.ZonesRepository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WorkerDomainFormState(
    val hostname: String = "",
    val zoneId: String? = null,
    val service: String = "",
    val isSaving: Boolean = false,
    val error: String? = null
)

data class WorkerDomainsUiState(
    val domains: UiState<List<WorkerDomain>> = UiState.Loading,
    val zones: List<CfZone> = emptyList(),
    val scripts: List<String> = emptyList(),
    val isRefreshing: Boolean = false,
    val form: WorkerDomainFormState? = null,
    val deletingId: String? = null,
    val error: String? = null
)

private val HOSTNAME_REGEX = Regex("^[a-zA-Z0-9]([a-zA-Z0-9-]*[a-zA-Z0-9])?(\\.[a-zA-Z0-9]([a-zA-Z0-9-]*[a-zA-Z0-9])?)+$")

fun validateWorkerDomainForm(form: WorkerDomainFormState): String? = when {
    form.hostname.isBlank() -> "Hostname is required"
    !form.hostname.trim().matches(HOSTNAME_REGEX) -> "Enter a hostname, e.g. api.example.com"
    form.zoneId == null -> "Pick the zone this hostname belongs to"
    form.service.isBlank() -> "Pick the Worker to bind"
    else -> null
}

/** "my-worker · example.com", the two facts a domain row carries. */
fun workerDomainSummary(domain: WorkerDomain): String =
    listOfNotNull(domain.service, domain.zoneName).joinToString(" · ")

class WorkerDomainsViewModel(
    private val accountId: String,
    private val repository: WorkersRepository,
    private val zonesRepository: ZonesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(WorkerDomainsUiState())
    val uiState: StateFlow<WorkerDomainsUiState> = _uiState.asStateFlow()

    init {
        load(isRefresh = false)
    }

    fun refresh() = load(isRefresh = true)

    /**
     * Attaching a domain needs a zone and a Worker, so both lists load alongside the domains -
     * a form that asked for raw ids would be unusable on a phone. All three run sequentially
     * in one coroutine.
     */
    private fun load(isRefresh: Boolean) {
        _uiState.update { if (isRefresh) it.copy(isRefreshing = true) else it.copy(domains = UiState.Loading) }
        viewModelScope.launch {
            when (val domains = repository.listDomains(accountId)) {
                is ApiResult.Success -> _uiState.update { it.copy(domains = UiState.Data(domains.data)) }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(domains = UiState.Error(ErrorClassifier.classify(domains)))
                }
            }
            when (val zones = zonesRepository.listZones()) {
                is ApiResult.Success -> _uiState.update { it.copy(zones = zones.data) }
                is ApiResult.Failure -> Unit
            }
            when (val scripts = repository.listScripts(accountId)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(scripts = scripts.data.map { script -> script.id }, isRefreshing = false)
                }
                is ApiResult.Failure -> _uiState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    fun openForm() = _uiState.update { state ->
        state.copy(
            form = WorkerDomainFormState(
                zoneId = state.zones.firstOrNull()?.id,
                service = state.scripts.firstOrNull().orEmpty()
            )
        )
    }

    fun closeForm() = _uiState.update { it.copy(form = null) }

    fun updateForm(transform: (WorkerDomainFormState) -> WorkerDomainFormState) =
        _uiState.update { state -> state.form?.let { state.copy(form = transform(it)) } ?: state }

    fun save() {
        val form = _uiState.value.form ?: return
        val validationError = validateWorkerDomainForm(form)
        if (validationError != null) {
            updateForm { it.copy(error = validationError) }
            return
        }
        val zoneId = form.zoneId ?: return
        updateForm { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            val result = repository.attachDomain(
                accountId = accountId,
                zoneId = zoneId,
                hostname = form.hostname.trim(),
                service = form.service
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

    fun delete(domain: WorkerDomain) {
        _uiState.update { it.copy(deletingId = domain.id, error = null) }
        viewModelScope.launch {
            when (val result = repository.detachDomain(accountId, domain.id)) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(deletingId = null) }
                    load(isRefresh = true)
                }
                is ApiResult.Failure -> _uiState.update { it.copy(deletingId = null, error = result.message) }
            }
        }
    }

    fun dismissError() = _uiState.update { it.copy(error = null) }
}

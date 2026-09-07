package dev.cfmobile.app.ui.certificates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cfmobile.app.core.errors.ErrorClassifier
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.dto.CertificatePack
import dev.cfmobile.app.data.remote.dto.CustomHostname
import dev.cfmobile.app.data.remote.dto.DnssecStatus
import dev.cfmobile.app.data.repository.CertificatesRepository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val HOSTNAME_REGEX = Regex("^[a-zA-Z0-9]([a-zA-Z0-9-]*[a-zA-Z0-9])?(\\.[a-zA-Z0-9]([a-zA-Z0-9-]*[a-zA-Z0-9])?)+$")

enum class CertificatesTab { CERTIFICATES, HOSTNAMES, DNSSEC }

/** How the custom hostname proves ownership. */
enum class HostnameValidationMethod(val apiValue: String, val label: String) {
    HTTP("http", "HTTP"),
    TXT("txt", "TXT record")
}

data class CustomHostnameFormState(
    val hostname: String = "",
    val method: HostnameValidationMethod = HostnameValidationMethod.HTTP,
    val isSaving: Boolean = false,
    val error: String? = null
)

data class CertificatesUiState(
    val tab: CertificatesTab = CertificatesTab.CERTIFICATES,
    val packs: UiState<List<CertificatePack>> = UiState.Loading,
    val hostnames: UiState<List<CustomHostname>> = UiState.Loading,
    val dnssec: DnssecStatus? = null,
    val dnssecError: String? = null,
    val isTogglingDnssec: Boolean = false,
    val isRefreshing: Boolean = false,
    val form: CustomHostnameFormState? = null,
    val deletingHostnameId: String? = null
)

fun validateHostname(hostname: String): String? = when {
    hostname.isBlank() -> "Hostname is required"
    !hostname.trim().matches(HOSTNAME_REGEX) -> "Enter a valid hostname, e.g. app.example.com"
    else -> null
}

/** DNSSEC is on only when Cloudflare reports it active; "pending" means the DS record still
 *  has to be published at the registrar, which is not the same as enabled. */
fun isDnssecActive(status: DnssecStatus?): Boolean = status?.status == "active"

fun dnssecStatusLabel(status: DnssecStatus?): String = when (status?.status) {
    "active" -> "Active"
    "pending" -> "Pending - add the DS record at your registrar"
    "pending-disabled" -> "Pending removal"
    "disabled", null -> "Disabled"
    else -> status.status.replaceFirstChar { it.uppercase() }
}

class CertificatesViewModel(
    private val zoneId: String,
    private val repository: CertificatesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CertificatesUiState())
    val uiState: StateFlow<CertificatesUiState> = _uiState.asStateFlow()

    init {
        load(isRefresh = false)
    }

    fun selectTab(tab: CertificatesTab) = _uiState.update { it.copy(tab = tab) }

    fun refresh() = load(isRefresh = true)

    private fun load(isRefresh: Boolean) {
        _uiState.update {
            if (isRefresh) it.copy(isRefreshing = true)
            else it.copy(packs = UiState.Loading, hostnames = UiState.Loading)
        }
        viewModelScope.launch {
            when (val packs = repository.listCertificatePacks(zoneId)) {
                is ApiResult.Success -> _uiState.update { it.copy(packs = UiState.Data(packs.data)) }
                is ApiResult.Failure -> _uiState.update { it.copy(packs = UiState.Error(ErrorClassifier.classify(packs))) }
            }
            when (val hostnames = repository.listCustomHostnames(zoneId)) {
                is ApiResult.Success -> _uiState.update { it.copy(hostnames = UiState.Data(hostnames.data)) }
                is ApiResult.Failure -> _uiState.update { it.copy(hostnames = UiState.Error(ErrorClassifier.classify(hostnames))) }
            }
            when (val dnssec = repository.getDnssec(zoneId)) {
                is ApiResult.Success -> _uiState.update { it.copy(dnssec = dnssec.data, dnssecError = null) }
                is ApiResult.Failure -> _uiState.update { it.copy(dnssecError = dnssec.message) }
            }
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    fun setDnssecEnabled(enabled: Boolean) {
        _uiState.update { it.copy(isTogglingDnssec = true, dnssecError = null) }
        viewModelScope.launch {
            when (val result = repository.setDnssecEnabled(zoneId, enabled)) {
                is ApiResult.Success -> _uiState.update { it.copy(dnssec = result.data, isTogglingDnssec = false) }
                // Leave the previous status in place rather than showing a state the zone
                // isn't actually in.
                is ApiResult.Failure -> _uiState.update { it.copy(isTogglingDnssec = false, dnssecError = result.message) }
            }
        }
    }

    fun openForm() = _uiState.update { it.copy(form = CustomHostnameFormState()) }
    fun closeForm() = _uiState.update { it.copy(form = null) }

    fun updateForm(transform: (CustomHostnameFormState) -> CustomHostnameFormState) =
        _uiState.update { state -> state.form?.let { state.copy(form = transform(it)) } ?: state }

    fun save() {
        val form = _uiState.value.form ?: return
        val validationError = validateHostname(form.hostname)
        if (validationError != null) {
            updateForm { it.copy(error = validationError) }
            return
        }
        updateForm { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.createCustomHostname(zoneId, form.hostname.trim(), form.method.apiValue)) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(form = null) }
                    load(isRefresh = true)
                }
                is ApiResult.Failure -> updateForm { it.copy(isSaving = false, error = result.message) }
            }
        }
    }

    fun deleteHostname(hostname: CustomHostname) {
        _uiState.update { it.copy(deletingHostnameId = hostname.id) }
        viewModelScope.launch {
            repository.deleteCustomHostname(zoneId, hostname.id)
            _uiState.update { it.copy(deletingHostnameId = null) }
            load(isRefresh = true)
        }
    }
}

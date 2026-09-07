package dev.cfmobile.app.ui.web3

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cfmobile.app.core.errors.ErrorClassifier
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.dto.Web3Hostname
import dev.cfmobile.app.data.repository.Web3Repository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The gateway kinds Cloudflare serves. IPFS universal path takes a CID in the URL; the
 *  DNSLink variant resolves one from a TXT record instead. */
enum class Web3Target(val value: String, val label: String, val description: String) {
    IPFS("ipfs", "IPFS (DNSLink)", "Resolves content from a _dnslink TXT record"),
    IPFS_UNIVERSAL("ipfs_universal_path", "IPFS (universal path)", "Serves any CID given in the URL path"),
    ETHEREUM("ethereum", "Ethereum", "Proxies JSON-RPC to Cloudflare's Ethereum gateway")
}

fun web3TargetFromValue(value: String?): Web3Target =
    Web3Target.entries.firstOrNull { it.value == value } ?: Web3Target.IPFS

data class Web3FormState(
    val name: String = "",
    val target: Web3Target = Web3Target.IPFS,
    val description: String = "",
    val dnslink: String = "",
    val isSaving: Boolean = false,
    val error: String? = null
)

data class Web3UiState(
    val hostnames: UiState<List<Web3Hostname>> = UiState.Loading,
    val isRefreshing: Boolean = false,
    val form: Web3FormState? = null,
    val deletingId: String? = null,
    val error: String? = null
)

private val HOSTNAME_REGEX = Regex("^[a-zA-Z0-9]([a-zA-Z0-9-]*[a-zA-Z0-9])?(\\.[a-zA-Z0-9]([a-zA-Z0-9-]*[a-zA-Z0-9])?)+$")

fun validateWeb3Form(form: Web3FormState): String? {
    val name = form.name.trim()
    if (name.isBlank()) return "A hostname is required"
    if (!name.matches(HOSTNAME_REGEX)) return "Enter a hostname, like web3.example.com"
    // Only the DNSLink flavour reads a path from the record; the others don't take one.
    if (form.target == Web3Target.IPFS && form.dnslink.isNotBlank() && !form.dnslink.trim().startsWith("/ipfs/")) {
        return "A DNSLink looks like /ipfs/<CID>"
    }
    return null
}

/** "IPFS (DNSLink) · active" - what the hostname serves and whether it's up. */
fun web3Summary(hostname: Web3Hostname): String = listOfNotNull(
    web3TargetFromValue(hostname.target).label,
    hostname.status
).joinToString(" · ")

class Web3ViewModel(
    private val zoneId: String,
    private val repository: Web3Repository
) : ViewModel() {

    private val _uiState = MutableStateFlow(Web3UiState())
    val uiState: StateFlow<Web3UiState> = _uiState.asStateFlow()

    init {
        load(isRefresh = false)
    }

    fun refresh() = load(isRefresh = true)

    private fun load(isRefresh: Boolean) {
        _uiState.update { if (isRefresh) it.copy(isRefreshing = true) else it.copy(hostnames = UiState.Loading) }
        viewModelScope.launch {
            when (val result = repository.listHostnames(zoneId)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(hostnames = UiState.Data(result.data), isRefreshing = false)
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(hostnames = UiState.Error(ErrorClassifier.classify(result)), isRefreshing = false)
                }
            }
        }
    }

    fun openForm() = _uiState.update { it.copy(form = Web3FormState()) }

    fun closeForm() = _uiState.update { it.copy(form = null) }

    fun updateForm(transform: (Web3FormState) -> Web3FormState) =
        _uiState.update { state -> state.form?.let { state.copy(form = transform(it)) } ?: state }

    fun save() {
        val form = _uiState.value.form ?: return
        val validationError = validateWeb3Form(form)
        if (validationError != null) {
            updateForm { it.copy(error = validationError) }
            return
        }
        updateForm { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            val result = repository.createHostname(
                zoneId = zoneId,
                name = form.name.trim(),
                target = form.target.value,
                description = form.description.trim().ifBlank { null },
                // Only the DNSLink flavour carries one.
                dnslink = form.dnslink.trim().takeIf { it.isNotBlank() && form.target == Web3Target.IPFS }
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

    fun delete(hostname: Web3Hostname) {
        _uiState.update { it.copy(deletingId = hostname.id, error = null) }
        viewModelScope.launch {
            when (val result = repository.deleteHostname(zoneId, hostname.id)) {
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

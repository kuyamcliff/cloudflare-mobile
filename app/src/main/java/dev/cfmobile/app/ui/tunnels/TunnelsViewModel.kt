package dev.cfmobile.app.ui.tunnels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cfmobile.app.core.errors.ErrorClassifier
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.dto.CfTunnel
import dev.cfmobile.app.data.repository.TunnelsRepository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TunnelFormState(val name: String = "", val isSaving: Boolean = false, val error: String? = null)

data class TunnelsUiState(
    val tunnels: UiState<List<CfTunnel>> = UiState.Loading,
    val isRefreshing: Boolean = false,
    val form: TunnelFormState? = null,
    val deletingId: String? = null
)

fun validateTunnelName(name: String): String? = if (name.isBlank()) "Tunnel name is required" else null

/** List/create/delete only - this registers a tunnel with Cloudflare, it doesn't run one.
 *  Actually connecting traffic through it needs the cloudflared daemon on a machine
 *  elsewhere, see CapabilityRegistry's migrationHint. */
class TunnelsViewModel(
    private val accountId: String,
    private val repository: TunnelsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TunnelsUiState())
    val uiState: StateFlow<TunnelsUiState> = _uiState.asStateFlow()

    init {
        load(isRefresh = false)
    }

    fun refresh() = load(isRefresh = true)

    /** [isRefresh] keeps the current list on screen during a pull-to-refresh, rather than
     *  replacing content the user is reading with a spinner. */
    private fun load(isRefresh: Boolean) {
        _uiState.update { if (isRefresh) it.copy(isRefreshing = true) else it.copy(tunnels = UiState.Loading) }
        viewModelScope.launch {
            when (val result = repository.listTunnels(accountId)) {
                is ApiResult.Success -> _uiState.update { it.copy(tunnels = UiState.Data(result.data), isRefreshing = false) }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(tunnels = UiState.Error(ErrorClassifier.classify(result)), isRefreshing = false)
                }
            }
        }
    }

    fun openForm() = _uiState.update { it.copy(form = TunnelFormState()) }
    fun closeForm() = _uiState.update { it.copy(form = null) }

    fun updateForm(transform: (TunnelFormState) -> TunnelFormState) =
        _uiState.update { state -> state.form?.let { state.copy(form = transform(it)) } ?: state }

    fun save() {
        val form = _uiState.value.form ?: return
        val validationError = validateTunnelName(form.name)
        if (validationError != null) {
            updateForm { it.copy(error = validationError) }
            return
        }
        updateForm { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.createTunnel(accountId, form.name.trim())) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(form = null) }
                    refresh()
                }
                is ApiResult.Failure -> updateForm { it.copy(isSaving = false, error = result.message) }
            }
        }
    }

    fun delete(tunnel: CfTunnel) {
        _uiState.update { it.copy(deletingId = tunnel.id) }
        viewModelScope.launch {
            repository.deleteTunnel(accountId, tunnel.id)
            _uiState.update { it.copy(deletingId = null) }
            refresh()
        }
    }
}

package dev.cfmobile.app.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cfmobile.app.core.errors.ErrorClassifier
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.dto.RegistrarDomain
import dev.cfmobile.app.data.repository.RegistrarRepository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RegistrarUiState(
    val domains: UiState<List<RegistrarDomain>> = UiState.Loading,
    val isRefreshing: Boolean = false
)

/** "Expires 2027-03-01 · auto-renew on · locked" - the three things worth knowing at a glance,
 *  each omitted when Cloudflare didn't report it. */
fun registrarSummary(domain: RegistrarDomain): String = listOfNotNull(
    domain.expiresAt?.let { "Expires $it" },
    domain.autoRenew?.let { if (it) "auto-renew on" else "auto-renew off" },
    domain.locked?.let { if (it) "locked" else "unlocked" }
).joinToString(" · ").ifBlank { "No registration details reported" }

/** A domain registered elsewhere still shows up here; saying which registrar holds it explains
 *  why its details are thin. */
fun registrarDetail(domain: RegistrarDomain): String? = domain.currentRegistrar?.let { "Registrar: $it" }

class RegistrarViewModel(
    private val accountId: String,
    private val repository: RegistrarRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegistrarUiState())
    val uiState: StateFlow<RegistrarUiState> = _uiState.asStateFlow()

    init {
        load(isRefresh = false)
    }

    fun refresh() = load(isRefresh = true)

    private fun load(isRefresh: Boolean) {
        _uiState.update { if (isRefresh) it.copy(isRefreshing = true) else it.copy(domains = UiState.Loading) }
        viewModelScope.launch {
            when (val result = repository.listDomains(accountId)) {
                is ApiResult.Success -> _uiState.update { it.copy(domains = UiState.Data(result.data), isRefreshing = false) }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(domains = UiState.Error(ErrorClassifier.classify(result)), isRefreshing = false)
                }
            }
        }
    }
}

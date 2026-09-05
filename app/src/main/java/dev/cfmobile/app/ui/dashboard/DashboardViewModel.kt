package dev.cfmobile.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cfmobile.app.data.local.AccountSummary
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.dto.CfAccount
import dev.cfmobile.app.data.repository.AccountsRepository
import dev.cfmobile.app.data.repository.AuthRepository
import dev.cfmobile.app.data.repository.ZonesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DashboardUiState(
    val isLoading: Boolean = true,
    val zoneCount: Int? = null,
    val cfAccounts: List<CfAccount> = emptyList(),
    val loadError: String? = null
)

/** The app's landing screen once a token is connected (PRD's dashboard-first navigation
 *  request): an account overview plus a menu into the app's main destinations, rather than
 *  dropping straight into the zone list with no sense that anything else exists. */
class DashboardViewModel(
    private val zonesRepository: ZonesRepository,
    private val accountsRepository: AccountsRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    val accounts: List<AccountSummary> get() = authRepository.savedAccounts
    val activeAccountId: String? get() = authRepository.activeAccount?.id
    val activeAccountLabel: String? get() = authRepository.activeAccount?.label
    val activeAccountEmail: String? get() = authRepository.activeAccount?.email

    init {
        refresh()
    }

    fun refresh() {
        _uiState.update { it.copy(isLoading = true, loadError = null) }
        viewModelScope.launch {
            val zonesResult = zonesRepository.listZones()
            val accountsResult = accountsRepository.listAccounts()
            _uiState.update {
                it.copy(
                    isLoading = false,
                    zoneCount = (zonesResult as? ApiResult.Success)?.data?.size,
                    cfAccounts = (accountsResult as? ApiResult.Success)?.data ?: emptyList(),
                    loadError = (zonesResult as? ApiResult.Failure)?.message
                )
            }
        }
    }

    fun switchAccount(accountId: String) {
        if (accountId == activeAccountId) return
        authRepository.switchTo(accountId)
        refresh()
    }
}

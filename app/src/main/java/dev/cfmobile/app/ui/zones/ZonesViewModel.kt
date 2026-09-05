package dev.cfmobile.app.ui.zones

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cfmobile.app.core.errors.ErrorClassifier
import dev.cfmobile.app.data.local.AccountSummary
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.dto.CfZone
import dev.cfmobile.app.data.repository.AuthRepository
import dev.cfmobile.app.data.repository.ZonesRepository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ZonesViewModel(
    private val repository: ZonesRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<List<CfZone>>>(UiState.Loading)
    val state: StateFlow<UiState<List<CfZone>>> = _state.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private var allZones: List<CfZone> = emptyList()

    val accounts: List<AccountSummary> get() = authRepository.savedAccounts
    val activeAccountId: String? get() = authRepository.activeAccount?.id
    val activeAccountLabel: String? get() = authRepository.activeAccount?.label

    init {
        refresh()
    }

    /** Re-fetches zones for whichever account is active right now. Called both from pull-to-
     *  refresh/retry and every time this screen resumes (PRD §49: switching accounts must
     *  never leave the previous account's zones on screen under the new account's context). */
    fun refresh() {
        _state.value = UiState.Loading
        viewModelScope.launch {
            when (val result = repository.listZones()) {
                is ApiResult.Success -> {
                    allZones = result.data
                    applyFilter()
                }
                is ApiResult.Failure -> _state.value = UiState.Error(ErrorClassifier.classify(result))
            }
        }
    }

    fun onQueryChange(value: String) {
        _query.value = value
        applyFilter()
    }

    /** Switches the active account and immediately refreshes - the query text is reset since
     *  it was scoped to the previous account's zones. */
    fun switchAccount(accountId: String) {
        if (accountId == activeAccountId) return
        authRepository.switchTo(accountId)
        _query.value = ""
        refresh()
    }

    private fun applyFilter() {
        val q = _query.value.trim().lowercase()
        val filtered = if (q.isEmpty()) allZones else allZones.filter { it.name.lowercase().contains(q) }
        _state.value = UiState.Data(filtered)
    }
}

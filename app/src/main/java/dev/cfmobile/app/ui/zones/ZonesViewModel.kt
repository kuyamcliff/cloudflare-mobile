package dev.cfmobile.app.ui.zones

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cfmobile.app.core.errors.ErrorClassifier
import dev.cfmobile.app.data.local.AccountSummary
import dev.cfmobile.app.data.local.db.ZonesCache
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
    private val authRepository: AuthRepository,
    private val zonesCache: ZonesCache
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<List<CfZone>>>(UiState.Loading)
    val state: StateFlow<UiState<List<CfZone>>> = _state.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _lastUpdatedAt = MutableStateFlow<Long?>(null)
    val lastUpdatedAt: StateFlow<Long?> = _lastUpdatedAt.asStateFlow()

    private var allZones: List<CfZone> = emptyList()

    val accounts: List<AccountSummary> get() = authRepository.savedAccounts
    val activeAccountId: String? get() = authRepository.activeAccount?.id
    val activeAccountLabel: String? get() = authRepository.activeAccount?.label

    init {
        refresh()
    }

    /** Re-fetches zones for whichever account is active right now. Called both from pull-to-
     *  refresh/retry and every time this screen resumes (PRD §49: switching accounts must
     *  never leave the previous account's zones on screen under the new account's context).
     *
     *  Cache-first (PRD §9): if nothing is loaded yet (first launch, or right after switching
     *  accounts), the last cached snapshot for this account is shown immediately - with
     *  FreshnessLabel disclosing its age - while the live fetch runs in the background. A
     *  background refresh failure never clears already-displayed data, cached or live; it's
     *  only surfaced as an error when there's nothing to show at all. */
    fun refresh() {
        val accountId = activeAccountId
        viewModelScope.launch {
            if (allZones.isEmpty() && accountId != null) {
                val cached = zonesCache.get(accountId)
                if (cached.zones.isNotEmpty()) {
                    allZones = cached.zones
                    _lastUpdatedAt.value = cached.cachedAt
                    applyFilter()
                }
            }
            if (_state.value !is UiState.Data) _state.value = UiState.Loading

            when (val result = repository.listZones()) {
                is ApiResult.Success -> {
                    val fetchedAt = System.currentTimeMillis()
                    // Persisted before the state update below, so by the time this screen
                    // shows "Data" the cache is already durable - no window where the UI
                    // claims freshness the cache doesn't actually have yet.
                    if (accountId != null) zonesCache.save(accountId, result.data, fetchedAt)
                    allZones = result.data
                    _lastUpdatedAt.value = fetchedAt
                    applyFilter()
                }
                is ApiResult.Failure -> if (allZones.isEmpty()) {
                    _state.value = UiState.Error(ErrorClassifier.classify(result))
                }
            }
        }
    }

    fun onQueryChange(value: String) {
        _query.value = value
        applyFilter()
    }

    /** Switches the active account and immediately refreshes - the query text is reset since
     *  it was scoped to the previous account's zones, and any zones already on screen are
     *  cleared first so the new account's cache (or a loading spinner) is what shows next,
     *  never the previous account's list. */
    fun switchAccount(accountId: String) {
        if (accountId == activeAccountId) return
        authRepository.switchTo(accountId)
        _query.value = ""
        allZones = emptyList()
        _lastUpdatedAt.value = null
        _state.value = UiState.Loading
        refresh()
    }

    private fun applyFilter() {
        val q = _query.value.trim().lowercase()
        val filtered = if (q.isEmpty()) allZones else allZones.filter { it.name.lowercase().contains(q) }
        _state.value = UiState.Data(filtered)
    }
}

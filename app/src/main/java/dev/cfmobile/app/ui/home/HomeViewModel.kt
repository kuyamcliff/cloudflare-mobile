package dev.cfmobile.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cfmobile.app.core.capabilities.CapabilityRepository
import dev.cfmobile.app.core.capabilities.CapabilityState
import dev.cfmobile.app.data.local.AccountSummary
import dev.cfmobile.app.data.local.ContextStore
import dev.cfmobile.app.data.local.NamedRef
import dev.cfmobile.app.data.local.WorkingContext
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.dto.CfAccount
import dev.cfmobile.app.data.remote.dto.CfZone
import dev.cfmobile.app.data.repository.AccountsRepository
import dev.cfmobile.app.data.repository.AuthRepository
import dev.cfmobile.app.data.repository.ZonesRepository
import dev.cfmobile.app.ui.navigation.Routes
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.temporal.ChronoUnit

data class QuickAction(val id: String, val label: String, val route: String)

data class HomeUiState(
    val profile: AccountSummary? = null,
    val profiles: List<AccountSummary> = emptyList(),
    val context: WorkingContext = WorkingContext(),
    val accounts: List<CfAccount> = emptyList(),
    val accountsError: String? = null,
    val zones: List<CfZone> = emptyList(),
    val zonesLoading: Boolean = false,
    val zonesError: String? = null,
    val zoneQuery: String = "",
    val quickActions: List<QuickAction> = emptyList(),
    val expiryWarning: String? = null,
    val loading: Boolean = true
)

/**
 * Home (spec 13, 150, 247). Focused on context and the next action: which profile, account
 * and zone the user is working in, what the token lets them do there, and where they were
 * recently. No aggregate "health score" or decorative cards.
 */
class HomeViewModel(
    private val auth: AuthRepository,
    private val accountsRepository: AccountsRepository,
    private val zonesRepository: ZonesRepository,
    private val contextStore: ContextStore,
    private val capabilities: CapabilityRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    private var zoneJob: Job? = null

    init {
        viewModelScope.launch {
            combine(contextStore.state, capabilities.state) { ctx, _ -> ctx }.collect { ctx ->
                _uiState.update { it.copy(context = ctx, profile = auth.activeAccount, profiles = auth.savedAccounts, expiryWarning = expiryWarning(auth.activeAccount)) }
                _uiState.update { it.copy(quickActions = quickActions(ctx)) }
            }
        }
        refresh()
    }

    fun refresh() {
        _uiState.update { it.copy(loading = true, profile = auth.activeAccount, profiles = auth.savedAccounts) }
        viewModelScope.launch {
            when (val r = accountsRepository.listAccounts()) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(accounts = r.data, accountsError = null, loading = false) }
                    val ctx = contextStore.state.value
                    // Keep the saved account if it is still visible; otherwise pick the first
                    // (spec 200: gracefully drop access that was removed).
                    val current = ctx.account?.takeIf { a -> r.data.any { it.id == a.id } }
                    val pick = current ?: r.data.firstOrNull()?.let { NamedRef(it.id, it.name) }
                    if (pick != null && pick != ctx.account) contextStore.selectAccount(pick)
                    loadZones()
                }
                is ApiResult.Failure -> {
                    _uiState.update { it.copy(accountsError = r.message, loading = false) }
                    loadZones()
                }
            }
        }
    }

    fun selectAccount(account: CfAccount) {
        contextStore.selectAccount(NamedRef(account.id, account.name))
        _uiState.update { it.copy(zoneQuery = "") }
        loadZones()
    }

    fun selectZone(zone: CfZone) = contextStore.selectZone(NamedRef(zone.id, zone.name, zone.account?.id))
    fun selectZoneRef(ref: NamedRef) = contextStore.selectZone(ref)
    fun clearZone() = contextStore.selectZone(null)
    fun toggleFavorite(ref: NamedRef) = contextStore.toggleFavoriteZone(ref)

    fun searchZones(q: String) {
        _uiState.update { it.copy(zoneQuery = q) }
        zoneJob?.cancel()
        zoneJob = viewModelScope.launch {
            delay(300) // debounce (spec 14)
            loadZonesNow()
        }
    }

    private fun loadZones() {
        zoneJob?.cancel()
        zoneJob = viewModelScope.launch { loadZonesNow() }
    }

    private suspend fun loadZonesNow() {
        val account = contextStore.state.value.account?.id
        _uiState.update { it.copy(zonesLoading = true, zonesError = null) }
        when (val r = zonesRepository.listZones(accountId = account, search = _uiState.value.zoneQuery, maxPages = 4)) {
            is ApiResult.Success -> _uiState.update { it.copy(zones = r.data, zonesLoading = false) }
            is ApiResult.Failure -> _uiState.update { it.copy(zonesLoading = false, zonesError = r.message) }
        }
    }

    /** Account products the token can use, for the Home shortcuts grid. Zone controls live on
     *  the zone hub, and everything else is in the command bar. */
    private suspend fun quickActions(ctx: WorkingContext): List<QuickAction> {
        val account = ctx.account?.id ?: return emptyList()
        val candidates = listOf(
            Triple("workers", "Workers", Routes.workers(account)),
            Triple("r2", "R2", Routes.r2(account)),
            Triple("kv", "KV", Routes.kv(account)),
            Triple("d1", "D1", Routes.d1(account)),
            Triple("pages", "Pages", Routes.pages(account)),
            Triple("tunnels", "Tunnels", Routes.tunnels(account)),
            Triple("access", "Access", Routes.access(account)),
            Triple("api_tokens", "API tokens", Routes.apiTokens(account))
        )
        return candidates.filter { (cap, _, _) ->
            capabilities.stateFor(cap, accountId = account) != CapabilityState.TOKEN_RESTRICTED
        }.map { (cap, label, route) -> QuickAction(cap, label, route) }
    }

    private fun expiryWarning(profile: AccountSummary?): String? {
        val expires = profile?.expiresOn?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return null
        val now = Instant.now()
        return when {
            expires.isBefore(now) -> "This profile's token expired on ${profile.expiresOn}. Connect a new token to keep managing this account."
            expires.isBefore(now.plus(14, ChronoUnit.DAYS)) -> "This profile's token expires on ${profile.expiresOn}."
            else -> null
        }
    }

}

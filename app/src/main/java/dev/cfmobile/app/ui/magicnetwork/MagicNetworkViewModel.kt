package dev.cfmobile.app.ui.magicnetwork

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cfmobile.app.core.errors.ErrorClassifier
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.dto.MagicGreTunnel
import dev.cfmobile.app.data.remote.dto.MagicIpsecTunnel
import dev.cfmobile.app.data.remote.dto.MagicRoute
import dev.cfmobile.app.data.remote.dto.MagicRouteWrite
import dev.cfmobile.app.data.remote.dto.MagicSite
import dev.cfmobile.app.data.remote.dto.MagicSiteLan
import dev.cfmobile.app.data.remote.dto.MagicSiteWan
import dev.cfmobile.app.data.repository.MagicNetworkRepository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class MagicTab(val label: String) {
    GRE("GRE"),
    IPSEC("IPsec"),
    ROUTES("Routes"),
    SITES("Sites")
}

data class MagicRouteFormState(
    val prefix: String = "",
    val nexthop: String = "",
    val priority: String = "100",
    val weight: String = "",
    val description: String = "",
    val isSaving: Boolean = false,
    val error: String? = null
)

/** The interfaces behind one site, loaded only when that site is opened. */
data class SiteInterfaces(
    val site: MagicSite,
    val lans: List<MagicSiteLan> = emptyList(),
    val wans: List<MagicSiteWan> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

data class MagicNetworkUiState(
    val tab: MagicTab = MagicTab.GRE,
    val greTunnels: UiState<List<MagicGreTunnel>> = UiState.Loading,
    val ipsecTunnels: UiState<List<MagicIpsecTunnel>> = UiState.Loading,
    val routes: UiState<List<MagicRoute>> = UiState.Loading,
    val sites: UiState<List<MagicSite>> = UiState.Loading,
    val isRefreshing: Boolean = false,
    val routeForm: MagicRouteFormState? = null,
    val deletingId: String? = null,
    val selectedSite: SiteInterfaces? = null,
    val error: String? = null
)

/** "203.0.113.1 → 198.51.100.1" for a tunnel's two endpoints. */
fun tunnelEndpointsLabel(cloudflareEndpoint: String?, customerEndpoint: String?): String? = when {
    cloudflareEndpoint != null && customerEndpoint != null -> "$cloudflareEndpoint → $customerEndpoint"
    cloudflareEndpoint != null -> cloudflareEndpoint
    else -> customerEndpoint
}

/** A prefix has to carry its mask: Magic WAN routes address space, not a single host. */
private val CIDR_REGEX = Regex("^[0-9a-fA-F.:]+/\\d{1,3}$")

fun validateRouteForm(form: MagicRouteFormState): String? {
    val prefix = form.prefix.trim()
    if (prefix.isBlank()) return "A prefix is required"
    if (!CIDR_REGEX.matches(prefix)) return "The prefix needs a mask, like 10.0.0.0/8"
    if (form.nexthop.trim().isBlank()) return "A next hop is required"
    val priority = form.priority.trim().toIntOrNull()
    if (priority == null || priority < 0) return "Priority must be a number, lowest wins"
    val weight = form.weight.trim()
    if (weight.isNotBlank() && (weight.toIntOrNull() ?: -1) < 0) return "Weight must be a number"
    return null
}

fun buildRouteWrite(form: MagicRouteFormState) = MagicRouteWrite(
    prefix = form.prefix.trim(),
    nexthop = form.nexthop.trim(),
    priority = form.priority.trim().toInt(),
    description = form.description.trim().ifBlank { null },
    weight = form.weight.trim().toIntOrNull()
)

/** "via 10.0.0.1 · priority 100" - what the route actually does. */
fun routeSummary(route: MagicRoute): String = listOfNotNull(
    route.nexthop?.let { "via $it" },
    route.priority?.let { "priority $it" }
).joinToString(" · ")

/** "2 connectors" / "1 connector" - a site's redundancy, at a glance. */
fun siteSummary(site: MagicSite): String {
    val connectors = listOfNotNull(site.connectorId, site.secondaryConnectorId).size
    return listOfNotNull(
        site.description,
        if (connectors > 0) "$connectors connector${if (connectors == 1) "" else "s"}" else "No connector bound",
        if (site.haMode == true) "high availability" else null
    ).joinToString(" · ")
}

class MagicNetworkViewModel(
    private val accountId: String,
    private val repository: MagicNetworkRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MagicNetworkUiState())
    val uiState: StateFlow<MagicNetworkUiState> = _uiState.asStateFlow()

    init {
        load(isRefresh = false)
    }

    fun selectTab(tab: MagicTab) = _uiState.update { it.copy(tab = tab) }

    fun refresh() = load(isRefresh = true)

    /** All four lists load sequentially in one coroutine, for the same reason as elsewhere:
     *  independent launches would race at the HTTP layer with nothing gained. */
    private fun load(isRefresh: Boolean) {
        _uiState.update {
            if (isRefresh) it.copy(isRefreshing = true)
            else it.copy(
                greTunnels = UiState.Loading,
                ipsecTunnels = UiState.Loading,
                routes = UiState.Loading,
                sites = UiState.Loading
            )
        }
        viewModelScope.launch {
            when (val result = repository.listGreTunnels(accountId)) {
                is ApiResult.Success -> _uiState.update { it.copy(greTunnels = UiState.Data(result.data)) }
                is ApiResult.Failure -> _uiState.update { it.copy(greTunnels = UiState.Error(ErrorClassifier.classify(result))) }
            }
            when (val result = repository.listIpsecTunnels(accountId)) {
                is ApiResult.Success -> _uiState.update { it.copy(ipsecTunnels = UiState.Data(result.data)) }
                is ApiResult.Failure -> _uiState.update { it.copy(ipsecTunnels = UiState.Error(ErrorClassifier.classify(result))) }
            }
            when (val result = repository.listRoutes(accountId)) {
                is ApiResult.Success -> _uiState.update { it.copy(routes = UiState.Data(result.data)) }
                is ApiResult.Failure -> _uiState.update { it.copy(routes = UiState.Error(ErrorClassifier.classify(result))) }
            }
            when (val result = repository.listSites(accountId)) {
                is ApiResult.Success -> _uiState.update { it.copy(sites = UiState.Data(result.data)) }
                is ApiResult.Failure -> _uiState.update { it.copy(sites = UiState.Error(ErrorClassifier.classify(result))) }
            }
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    fun openRouteForm() = _uiState.update { it.copy(routeForm = MagicRouteFormState()) }

    fun closeRouteForm() = _uiState.update { it.copy(routeForm = null) }

    fun updateRouteForm(transform: (MagicRouteFormState) -> MagicRouteFormState) =
        _uiState.update { state -> state.routeForm?.let { state.copy(routeForm = transform(it)) } ?: state }

    fun saveRoute() {
        val form = _uiState.value.routeForm ?: return
        val validationError = validateRouteForm(form)
        if (validationError != null) {
            updateRouteForm { it.copy(error = validationError) }
            return
        }
        updateRouteForm { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.createRoute(accountId, buildRouteWrite(form))) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(routeForm = null) }
                    load(isRefresh = true)
                }
                is ApiResult.Failure -> updateRouteForm { it.copy(isSaving = false, error = result.message) }
            }
        }
    }

    fun deleteRoute(route: MagicRoute) {
        _uiState.update { it.copy(deletingId = route.id, error = null) }
        viewModelScope.launch {
            when (val result = repository.deleteRoute(accountId, route.id)) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(deletingId = null) }
                    load(isRefresh = true)
                }
                is ApiResult.Failure -> _uiState.update { it.copy(deletingId = null, error = result.message) }
            }
        }
    }

    /** A site's LANs and WANs are two more calls, made only when the site is opened. */
    fun openSite(site: MagicSite) {
        _uiState.update { it.copy(selectedSite = SiteInterfaces(site = site)) }
        viewModelScope.launch {
            val lans = repository.listSiteLans(accountId, site.id)
            val wans = repository.listSiteWans(accountId, site.id)
            _uiState.update { state ->
                if (state.selectedSite?.site?.id != site.id) return@update state
                state.copy(
                    selectedSite = state.selectedSite.copy(
                        lans = (lans as? ApiResult.Success)?.data.orEmpty(),
                        wans = (wans as? ApiResult.Success)?.data.orEmpty(),
                        isLoading = false,
                        error = (lans as? ApiResult.Failure)?.message ?: (wans as? ApiResult.Failure)?.message
                    )
                )
            }
        }
    }

    fun closeSite() = _uiState.update { it.copy(selectedSite = null) }

    fun dismissError() = _uiState.update { it.copy(error = null) }
}

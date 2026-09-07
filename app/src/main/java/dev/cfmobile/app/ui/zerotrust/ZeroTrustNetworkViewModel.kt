package dev.cfmobile.app.ui.zerotrust

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cfmobile.app.core.errors.ErrorClassifier
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.dto.DeviceSettingsPolicy
import dev.cfmobile.app.data.remote.dto.GatewayLocation
import dev.cfmobile.app.data.remote.dto.GatewayLocationNetwork
import dev.cfmobile.app.data.remote.dto.TunnelRoute
import dev.cfmobile.app.data.remote.dto.CfTunnel
import dev.cfmobile.app.data.remote.dto.VirtualNetwork
import dev.cfmobile.app.data.repository.DevicePostureRepository
import dev.cfmobile.app.data.repository.GatewayRepository
import dev.cfmobile.app.data.repository.TunnelsRepository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The private-network side of Zero Trust: what's routable, and how clients reach it. */
enum class ZeroTrustNetworkTab(val label: String) {
    ROUTES("Routes"),
    VIRTUAL_NETWORKS("Virtual networks"),
    LOCATIONS("DNS locations"),
    WARP("WARP profiles")
}

data class TunnelRouteFormState(
    val network: String = "",
    val tunnelId: String? = null,
    val virtualNetworkId: String? = null,
    val comment: String = "",
    val isSaving: Boolean = false,
    val error: String? = null
)

data class VirtualNetworkFormState(
    val name: String = "",
    val comment: String = "",
    val isSaving: Boolean = false,
    val error: String? = null
)

data class GatewayLocationFormState(
    val name: String = "",
    /** One CIDR per line - the source networks whose DNS arrives at this location. */
    val networks: String = "",
    val clientDefault: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null
)

data class ZeroTrustNetworkUiState(
    val tab: ZeroTrustNetworkTab = ZeroTrustNetworkTab.ROUTES,
    val routes: UiState<List<TunnelRoute>> = UiState.Loading,
    val virtualNetworks: UiState<List<VirtualNetwork>> = UiState.Loading,
    val locations: UiState<List<GatewayLocation>> = UiState.Loading,
    val warpProfiles: UiState<List<DeviceSettingsPolicy>> = UiState.Loading,
    val tunnels: List<CfTunnel> = emptyList(),
    val isRefreshing: Boolean = false,
    val routeForm: TunnelRouteFormState? = null,
    val virtualNetworkForm: VirtualNetworkFormState? = null,
    val locationForm: GatewayLocationFormState? = null,
    val deletingId: String? = null,
    val error: String? = null
)

/** A CIDR block, which is what both a tunnel route and a Gateway location's network take. */
private val CIDR_REGEX = Regex("^((25[0-5]|2[0-4]\\d|1?\\d?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1?\\d?\\d)/(3[0-2]|[12]?\\d)$")

fun isCidr(value: String): Boolean = value.trim().matches(CIDR_REGEX)

fun validateRouteForm(form: TunnelRouteFormState): String? = when {
    form.network.isBlank() -> "A network is required"
    !isCidr(form.network) -> "Enter a CIDR range, e.g. 10.0.0.0/8"
    form.tunnelId == null -> "Pick the tunnel that reaches this network"
    else -> null
}

fun validateVirtualNetworkForm(form: VirtualNetworkFormState): String? =
    if (form.name.isBlank()) "Name is required" else null

fun validateLocationForm(form: GatewayLocationFormState): String? {
    if (form.name.isBlank()) return "Location name is required"
    val networks = splitLines(form.networks)
    networks.firstOrNull { !isCidr(it) }?.let { return "\"$it\" isn't a CIDR range" }
    return null
}

fun locationNetworks(form: GatewayLocationFormState): List<GatewayLocationNetwork> =
    splitLines(form.networks).map { GatewayLocationNetwork(network = it) }

/** "through my-tunnel · vnet default", from whatever the route reported. */
fun routeSummary(route: TunnelRoute): String = listOfNotNull(
    route.tunnelName?.let { "through $it" },
    route.comment?.takeIf { it.isNotBlank() }
).joinToString(" · ").ifBlank { route.tunnelId ?: "No tunnel reported" }

fun locationSummary(location: GatewayLocation): String = listOfNotNull(
    location.dohSubdomain?.let { "$it.cloudflare-gateway.com" },
    location.networks?.size?.takeIf { it > 0 }?.let { "$it network${if (it == 1) "" else "s"}" },
    if (location.clientDefault == true) "default" else null
).joinToString(" · ")

/** WARP's service mode decides how much traffic the client takes over. */
fun warpProfileSummary(profile: DeviceSettingsPolicy): String = listOfNotNull(
    profile.serviceMode?.mode?.let { "mode $it" },
    profile.precedence?.let { "precedence $it" },
    if (profile.default == true) "default profile" else null,
    if (profile.enabled == false) "disabled" else null
).joinToString(" · ").ifBlank { profile.match ?: "No settings reported" }

class ZeroTrustNetworkViewModel(
    private val accountId: String,
    private val tunnelsRepository: TunnelsRepository,
    private val gatewayRepository: GatewayRepository,
    private val devicePostureRepository: DevicePostureRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ZeroTrustNetworkUiState())
    val uiState: StateFlow<ZeroTrustNetworkUiState> = _uiState.asStateFlow()

    init {
        load(isRefresh = false)
    }

    fun refresh() = load(isRefresh = true)

    fun selectTab(tab: ZeroTrustNetworkTab) = _uiState.update { it.copy(tab = tab) }

    /** Five requests across three repositories, sequential in one coroutine. The tunnel list is
     *  loaded so the route form can pick a tunnel by name rather than by id. */
    private fun load(isRefresh: Boolean) {
        _uiState.update {
            if (isRefresh) it.copy(isRefreshing = true)
            else it.copy(
                routes = UiState.Loading,
                virtualNetworks = UiState.Loading,
                locations = UiState.Loading,
                warpProfiles = UiState.Loading
            )
        }
        viewModelScope.launch {
            when (val routes = tunnelsRepository.listRoutes(accountId)) {
                is ApiResult.Success -> _uiState.update { it.copy(routes = UiState.Data(routes.data)) }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(routes = UiState.Error(ErrorClassifier.classify(routes)))
                }
            }
            when (val networks = tunnelsRepository.listVirtualNetworks(accountId)) {
                is ApiResult.Success -> _uiState.update { it.copy(virtualNetworks = UiState.Data(networks.data)) }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(virtualNetworks = UiState.Error(ErrorClassifier.classify(networks)))
                }
            }
            when (val tunnels = tunnelsRepository.listTunnels(accountId)) {
                is ApiResult.Success -> _uiState.update { it.copy(tunnels = tunnels.data) }
                is ApiResult.Failure -> Unit
            }
            when (val locations = gatewayRepository.listLocations(accountId)) {
                is ApiResult.Success -> _uiState.update { it.copy(locations = UiState.Data(locations.data)) }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(locations = UiState.Error(ErrorClassifier.classify(locations)))
                }
            }
            when (val profiles = devicePostureRepository.listSettingsPolicies(accountId)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(warpProfiles = UiState.Data(profiles.data), isRefreshing = false)
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(warpProfiles = UiState.Error(ErrorClassifier.classify(profiles)), isRefreshing = false)
                }
            }
        }
    }

    fun openForm() = _uiState.update { state ->
        when (state.tab) {
            ZeroTrustNetworkTab.ROUTES -> state.copy(
                routeForm = TunnelRouteFormState(tunnelId = state.tunnels.firstOrNull()?.id)
            )
            ZeroTrustNetworkTab.VIRTUAL_NETWORKS -> state.copy(virtualNetworkForm = VirtualNetworkFormState())
            ZeroTrustNetworkTab.LOCATIONS -> state.copy(locationForm = GatewayLocationFormState())
            // WARP profiles carry split tunnel and fallback-domain lists that need their own
            // screen to edit, so this tab is read-only.
            ZeroTrustNetworkTab.WARP -> state
        }
    }

    fun closeForms() = _uiState.update {
        it.copy(routeForm = null, virtualNetworkForm = null, locationForm = null)
    }

    fun updateRouteForm(transform: (TunnelRouteFormState) -> TunnelRouteFormState) =
        _uiState.update { state -> state.routeForm?.let { state.copy(routeForm = transform(it)) } ?: state }

    fun updateVirtualNetworkForm(transform: (VirtualNetworkFormState) -> VirtualNetworkFormState) =
        _uiState.update { state ->
            state.virtualNetworkForm?.let { state.copy(virtualNetworkForm = transform(it)) } ?: state
        }

    fun updateLocationForm(transform: (GatewayLocationFormState) -> GatewayLocationFormState) =
        _uiState.update { state -> state.locationForm?.let { state.copy(locationForm = transform(it)) } ?: state }

    fun saveRoute() {
        val form = _uiState.value.routeForm ?: return
        val validationError = validateRouteForm(form)
        if (validationError != null) {
            updateRouteForm { it.copy(error = validationError) }
            return
        }
        val tunnelId = form.tunnelId ?: return
        updateRouteForm { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            val result = tunnelsRepository.createRoute(
                accountId = accountId,
                network = form.network.trim(),
                tunnelId = tunnelId,
                comment = form.comment.trim().ifBlank { null },
                virtualNetworkId = form.virtualNetworkId
            )
            when (result) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(routeForm = null) }
                    load(isRefresh = true)
                }
                is ApiResult.Failure -> updateRouteForm { it.copy(isSaving = false, error = result.message) }
            }
        }
    }

    fun saveVirtualNetwork() {
        val form = _uiState.value.virtualNetworkForm ?: return
        val validationError = validateVirtualNetworkForm(form)
        if (validationError != null) {
            updateVirtualNetworkForm { it.copy(error = validationError) }
            return
        }
        updateVirtualNetworkForm { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            val result = tunnelsRepository.createVirtualNetwork(
                accountId,
                form.name.trim(),
                form.comment.trim().ifBlank { null }
            )
            when (result) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(virtualNetworkForm = null) }
                    load(isRefresh = true)
                }
                is ApiResult.Failure -> updateVirtualNetworkForm { it.copy(isSaving = false, error = result.message) }
            }
        }
    }

    fun saveLocation() {
        val form = _uiState.value.locationForm ?: return
        val validationError = validateLocationForm(form)
        if (validationError != null) {
            updateLocationForm { it.copy(error = validationError) }
            return
        }
        updateLocationForm { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            val result = gatewayRepository.createLocation(
                accountId,
                form.name.trim(),
                form.clientDefault,
                locationNetworks(form)
            )
            when (result) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(locationForm = null) }
                    load(isRefresh = true)
                }
                is ApiResult.Failure -> updateLocationForm { it.copy(isSaving = false, error = result.message) }
            }
        }
    }

    fun deleteRoute(route: TunnelRoute) = delete(route.id) { tunnelsRepository.deleteRoute(accountId, route.id) }

    fun deleteVirtualNetwork(network: VirtualNetwork) =
        delete(network.id) { tunnelsRepository.deleteVirtualNetwork(accountId, network.id) }

    fun deleteLocation(location: GatewayLocation) =
        delete(location.id) { gatewayRepository.deleteLocation(accountId, location.id) }

    private fun delete(id: String, request: suspend () -> ApiResult<Unit>) {
        _uiState.update { it.copy(deletingId = id, error = null) }
        viewModelScope.launch {
            when (val result = request()) {
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

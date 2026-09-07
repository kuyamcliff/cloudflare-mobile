package dev.cfmobile.app.ui.loadbalancing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cfmobile.app.core.errors.ErrorClassifier
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.dto.CfZone
import dev.cfmobile.app.data.remote.dto.LoadBalancer
import dev.cfmobile.app.data.remote.dto.LoadBalancerMonitor
import dev.cfmobile.app.data.remote.dto.LoadBalancerMonitorWrite
import dev.cfmobile.app.data.remote.dto.LoadBalancerOrigin
import dev.cfmobile.app.data.remote.dto.LoadBalancerPool
import dev.cfmobile.app.data.remote.dto.LoadBalancerPoolWrite
import dev.cfmobile.app.data.remote.dto.LoadBalancerWrite
import dev.cfmobile.app.data.repository.LoadBalancingRepository
import dev.cfmobile.app.data.repository.ZonesRepository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OriginFormState(val name: String = "", val address: String = "", val enabled: Boolean = true)

data class PoolFormState(
    val name: String = "",
    val origins: List<OriginFormState> = listOf(OriginFormState()),
    /** The health monitor to attach; null means the pool never marks an origin unhealthy. */
    val monitorId: String? = null,
    val isSaving: Boolean = false,
    val error: String? = null
)

/** HTTP is the monitor type worth offering from a phone: TCP and UDP monitors exist but need
 *  port and protocol detail this form doesn't collect. */
data class MonitorFormState(
    val description: String = "",
    val path: String = "/",
    val expectedCodes: String = "2xx",
    val interval: String = "60",
    val retries: String = "2",
    val timeout: String = "5",
    val isSaving: Boolean = false,
    val error: String? = null
)

/** [poolId] null means "no pools exist yet" - the form still opens so the user sees why they
 *  can't save, rather than the FAB silently doing nothing. Uses a single pool for both
 *  default_pools and fallback_pool (PRD scope trim: multi-pool steering/priority isn't
 *  supported from this form - see CapabilityRegistry's migrationHint). */
data class LbFormState(
    val hostname: String = "",
    val poolId: String? = null,
    val proxied: Boolean = true,
    val enabled: Boolean = true,
    val isSaving: Boolean = false,
    val error: String? = null
)

data class LoadBalancingUiState(
    val pools: UiState<List<LoadBalancerPool>> = UiState.Loading,
    val monitors: UiState<List<LoadBalancerMonitor>> = UiState.Loading,
    val monitorForm: MonitorFormState? = null,
    val deletingMonitorId: String? = null,
    val poolForm: PoolFormState? = null,
    val deletingPoolId: String? = null,
    val zones: List<CfZone> = emptyList(),
    val selectedZoneId: String? = null,
    val loadBalancers: UiState<List<LoadBalancer>> = UiState.Loading,
    val lbForm: LbFormState? = null,
    val deletingLbId: String? = null
)

fun validatePoolForm(form: PoolFormState): String? = when {
    form.name.isBlank() -> "Pool name is required"
    form.origins.none { it.address.isNotBlank() } -> "At least one origin address is required"
    else -> null
}

fun buildPoolWrite(form: PoolFormState): LoadBalancerPoolWrite = LoadBalancerPoolWrite(
    name = form.name.trim(),
    origins = form.origins.filter { it.address.isNotBlank() }.mapIndexed { index, origin ->
        LoadBalancerOrigin(name = origin.name.trim().ifBlank { "origin-${index + 1}" }, address = origin.address.trim(), enabled = origin.enabled)
    },
    monitor = form.monitorId
)

/** Cloudflare's own bounds: an interval under 60s needs a plan that allows it, and the timeout
 *  has to fit inside the interval or checks overlap. */
fun validateMonitorForm(form: MonitorFormState): String? {
    val interval = form.interval.trim().toIntOrNull()
    val retries = form.retries.trim().toIntOrNull()
    val timeout = form.timeout.trim().toIntOrNull()
    return when {
        !form.path.trim().startsWith("/") -> "The path must start with /"
        form.expectedCodes.isBlank() -> "Expected status codes are required, e.g. 2xx"
        interval == null || interval < 10 -> "Interval must be at least 10 seconds"
        retries == null || retries < 0 -> "Retries must be zero or more"
        timeout == null || timeout < 1 -> "Timeout must be at least 1 second"
        timeout >= interval -> "Timeout has to be shorter than the interval, or checks overlap"
        else -> null
    }
}

fun buildMonitorWrite(form: MonitorFormState): LoadBalancerMonitorWrite = LoadBalancerMonitorWrite(
    type = "http",
    description = form.description.trim().ifBlank { null },
    method = "GET",
    path = form.path.trim(),
    expectedCodes = form.expectedCodes.trim(),
    interval = form.interval.trim().toInt(),
    retries = form.retries.trim().toInt(),
    timeout = form.timeout.trim().toInt()
)

/** "GET /health · every 60s" - what the monitor actually does. */
fun monitorSummary(monitor: LoadBalancerMonitor): String = listOfNotNull(
    listOfNotNull(monitor.method, monitor.path).joinToString(" ").takeIf { it.isNotBlank() },
    monitor.interval?.let { "every ${it}s" },
    monitor.expectedCodes?.let { "expects $it" }
).joinToString(" · ")

/** A pool with no monitor still serves traffic - it just never fails an origin out, which is
 *  worth saying on the row rather than leaving blank. */
fun poolMonitorLabel(pool: LoadBalancerPool, monitors: List<LoadBalancerMonitor>): String {
    val monitorId = pool.monitor ?: return "No health monitor - no automatic failover"
    val monitor = monitors.firstOrNull { it.id == monitorId }
    return monitor?.description?.takeIf { it.isNotBlank() }
        ?: monitor?.let { monitorSummary(it) }?.takeIf { it.isNotBlank() }
        ?: "Monitor $monitorId"
}

fun validateLbForm(form: LbFormState): String? = when {
    form.hostname.isBlank() -> "Hostname is required"
    form.poolId == null -> "Create a pool first"
    else -> null
}

fun buildLoadBalancerWrite(form: LbFormState): LoadBalancerWrite {
    val poolId = requireNotNull(form.poolId)
    return LoadBalancerWrite(
        name = form.hostname.trim(),
        enabled = form.enabled,
        proxied = form.proxied,
        defaultPools = listOf(poolId),
        fallbackPool = poolId
    )
}

class LoadBalancingViewModel(
    private val accountId: String,
    private val repository: LoadBalancingRepository,
    private val zonesRepository: ZonesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoadBalancingUiState())
    val uiState: StateFlow<LoadBalancingUiState> = _uiState.asStateFlow()

    // Pools and zones/load-balancers are loaded sequentially in one coroutine rather than two
    // independently-launched ones - both would otherwise fire real, concurrent HTTP requests
    // whose actual arrival order at the server is a genuine race (not something the coroutine
    // dispatcher controls), which is both pointless to parallelize here and made the load
    // sequence non-deterministic.
    init {
        viewModelScope.launch {
            loadPools()
            loadMonitors()
            loadZonesThenLoadBalancers()
        }
    }

    fun refreshMonitors() {
        viewModelScope.launch { loadMonitors() }
    }

    private suspend fun loadMonitors() {
        _uiState.update { it.copy(monitors = UiState.Loading) }
        when (val result = repository.listMonitors(accountId)) {
            is ApiResult.Success -> _uiState.update { it.copy(monitors = UiState.Data(result.data)) }
            is ApiResult.Failure -> _uiState.update { it.copy(monitors = UiState.Error(ErrorClassifier.classify(result))) }
        }
    }

    fun openMonitorForm() = _uiState.update { it.copy(monitorForm = MonitorFormState()) }

    fun closeMonitorForm() = _uiState.update { it.copy(monitorForm = null) }

    fun updateMonitorForm(transform: (MonitorFormState) -> MonitorFormState) =
        _uiState.update { state -> state.monitorForm?.let { state.copy(monitorForm = transform(it)) } ?: state }

    fun saveMonitor() {
        val form = _uiState.value.monitorForm ?: return
        val validationError = validateMonitorForm(form)
        if (validationError != null) {
            updateMonitorForm { it.copy(error = validationError) }
            return
        }
        updateMonitorForm { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.createMonitor(accountId, buildMonitorWrite(form))) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(monitorForm = null) }
                    loadMonitors()
                }
                is ApiResult.Failure -> updateMonitorForm { it.copy(isSaving = false, error = result.message) }
            }
        }
    }

    fun deleteMonitor(monitor: LoadBalancerMonitor) {
        _uiState.update { it.copy(deletingMonitorId = monitor.id) }
        viewModelScope.launch {
            repository.deleteMonitor(accountId, monitor.id)
            _uiState.update { it.copy(deletingMonitorId = null) }
            loadMonitors()
            // A deleted monitor leaves the pools that referenced it without one.
            loadPools()
        }
    }

    fun refreshPools() {
        viewModelScope.launch { loadPools() }
    }

    private suspend fun loadPools() {
        _uiState.update { it.copy(pools = UiState.Loading) }
        when (val result = repository.listPools(accountId)) {
            is ApiResult.Success -> _uiState.update { it.copy(pools = UiState.Data(result.data)) }
            is ApiResult.Failure -> _uiState.update { it.copy(pools = UiState.Error(ErrorClassifier.classify(result))) }
        }
    }

    private suspend fun loadZonesThenLoadBalancers() {
        when (val result = zonesRepository.listZones()) {
            is ApiResult.Success -> {
                _uiState.update { state -> state.copy(zones = result.data, selectedZoneId = state.selectedZoneId ?: result.data.firstOrNull()?.id) }
                if (result.data.isEmpty()) {
                    // No zone to pick means there's nothing to fetch load balancers for -
                    // resolve to an empty list rather than leaving this tab spinning forever.
                    _uiState.update { it.copy(loadBalancers = UiState.Data(emptyList())) }
                } else {
                    loadLoadBalancersForSelectedZone()
                }
            }
            is ApiResult.Failure -> _uiState.update { it.copy(loadBalancers = UiState.Error(ErrorClassifier.classify(result))) }
        }
    }

    fun selectZone(zoneId: String) {
        if (zoneId == _uiState.value.selectedZoneId) return
        _uiState.update { it.copy(selectedZoneId = zoneId) }
        refreshLoadBalancers()
    }

    fun refreshLoadBalancers() {
        viewModelScope.launch { loadLoadBalancersForSelectedZone() }
    }

    private suspend fun loadLoadBalancersForSelectedZone() {
        val zoneId = _uiState.value.selectedZoneId ?: return
        _uiState.update { it.copy(loadBalancers = UiState.Loading) }
        when (val result = repository.listLoadBalancers(zoneId)) {
            is ApiResult.Success -> _uiState.update { it.copy(loadBalancers = UiState.Data(result.data)) }
            is ApiResult.Failure -> _uiState.update { it.copy(loadBalancers = UiState.Error(ErrorClassifier.classify(result))) }
        }
    }

    fun openPoolForm() = _uiState.update { it.copy(poolForm = PoolFormState()) }
    fun closePoolForm() = _uiState.update { it.copy(poolForm = null) }

    fun updatePoolForm(transform: (PoolFormState) -> PoolFormState) =
        _uiState.update { state -> state.poolForm?.let { state.copy(poolForm = transform(it)) } ?: state }

    fun addOriginRow() = updatePoolForm { it.copy(origins = it.origins + OriginFormState()) }

    fun removeOriginRow(index: Int) = updatePoolForm { it.copy(origins = it.origins.filterIndexed { i, _ -> i != index }) }

    fun updateOrigin(index: Int, transform: (OriginFormState) -> OriginFormState) = updatePoolForm { form ->
        form.copy(origins = form.origins.mapIndexed { i, origin -> if (i == index) transform(origin) else origin })
    }

    fun savePool() {
        val form = _uiState.value.poolForm ?: return
        val validationError = validatePoolForm(form)
        if (validationError != null) {
            updatePoolForm { it.copy(error = validationError) }
            return
        }
        updatePoolForm { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.createPool(accountId, buildPoolWrite(form))) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(poolForm = null) }
                    refreshPools()
                }
                is ApiResult.Failure -> updatePoolForm { it.copy(isSaving = false, error = result.message) }
            }
        }
    }

    fun deletePool(pool: LoadBalancerPool) {
        _uiState.update { it.copy(deletingPoolId = pool.id) }
        viewModelScope.launch {
            repository.deletePool(accountId, pool.id)
            _uiState.update { it.copy(deletingPoolId = null) }
            refreshPools()
        }
    }

    fun openLbForm() = _uiState.update {
        val firstPoolId = (it.pools as? UiState.Data)?.value?.firstOrNull()?.id
        it.copy(lbForm = LbFormState(poolId = firstPoolId))
    }

    fun closeLbForm() = _uiState.update { it.copy(lbForm = null) }

    fun updateLbForm(transform: (LbFormState) -> LbFormState) =
        _uiState.update { state -> state.lbForm?.let { state.copy(lbForm = transform(it)) } ?: state }

    fun saveLoadBalancer() {
        val zoneId = _uiState.value.selectedZoneId ?: return
        val form = _uiState.value.lbForm ?: return
        val validationError = validateLbForm(form)
        if (validationError != null) {
            updateLbForm { it.copy(error = validationError) }
            return
        }
        updateLbForm { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.createLoadBalancer(zoneId, buildLoadBalancerWrite(form))) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(lbForm = null) }
                    refreshLoadBalancers()
                }
                is ApiResult.Failure -> updateLbForm { it.copy(isSaving = false, error = result.message) }
            }
        }
    }

    fun deleteLoadBalancer(loadBalancer: LoadBalancer) {
        val zoneId = _uiState.value.selectedZoneId ?: return
        _uiState.update { it.copy(deletingLbId = loadBalancer.id) }
        viewModelScope.launch {
            repository.deleteLoadBalancer(zoneId, loadBalancer.id)
            _uiState.update { it.copy(deletingLbId = null) }
            refreshLoadBalancers()
        }
    }
}

package dev.cfmobile.app.ui.loadbalancing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cfmobile.app.core.errors.ErrorClassifier
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.dto.CfZone
import dev.cfmobile.app.data.remote.dto.LoadBalancer
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
    }
)

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

    init {
        refreshPools()
        loadZones()
    }

    fun refreshPools() {
        _uiState.update { it.copy(pools = UiState.Loading) }
        viewModelScope.launch {
            when (val result = repository.listPools(accountId)) {
                is ApiResult.Success -> _uiState.update { it.copy(pools = UiState.Data(result.data)) }
                is ApiResult.Failure -> _uiState.update { it.copy(pools = UiState.Error(ErrorClassifier.classify(result))) }
            }
        }
    }

    private fun loadZones() {
        viewModelScope.launch {
            when (val result = zonesRepository.listZones()) {
                is ApiResult.Success -> {
                    _uiState.update { state -> state.copy(zones = result.data, selectedZoneId = state.selectedZoneId ?: result.data.firstOrNull()?.id) }
                    if (result.data.isEmpty()) {
                        // No zone to pick means there's nothing to fetch load balancers for -
                        // resolve to an empty list rather than leaving this tab spinning forever.
                        _uiState.update { it.copy(loadBalancers = UiState.Data(emptyList())) }
                    } else {
                        refreshLoadBalancers()
                    }
                }
                is ApiResult.Failure -> _uiState.update { it.copy(loadBalancers = UiState.Error(ErrorClassifier.classify(result))) }
            }
        }
    }

    fun selectZone(zoneId: String) {
        if (zoneId == _uiState.value.selectedZoneId) return
        _uiState.update { it.copy(selectedZoneId = zoneId) }
        refreshLoadBalancers()
    }

    fun refreshLoadBalancers() {
        val zoneId = _uiState.value.selectedZoneId ?: return
        _uiState.update { it.copy(loadBalancers = UiState.Loading) }
        viewModelScope.launch {
            when (val result = repository.listLoadBalancers(zoneId)) {
                is ApiResult.Success -> _uiState.update { it.copy(loadBalancers = UiState.Data(result.data)) }
                is ApiResult.Failure -> _uiState.update { it.copy(loadBalancers = UiState.Error(ErrorClassifier.classify(result))) }
            }
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

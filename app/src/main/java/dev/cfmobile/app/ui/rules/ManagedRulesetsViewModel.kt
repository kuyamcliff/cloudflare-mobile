package dev.cfmobile.app.ui.rules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cfmobile.app.core.errors.ErrorClassifier
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.dto.RuleActionParameters
import dev.cfmobile.app.data.remote.dto.Ruleset
import dev.cfmobile.app.data.remote.dto.RulesetRuleWrite
import dev.cfmobile.app.data.repository.RulesetPhaseRepository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Cloudflare's managed WAF rulesets are deployed by "execute" rules in this phase. */
const val MANAGED_WAF_PHASE = "http_request_firewall_managed"
const val EXECUTE_ACTION = "execute"

/**
 * One managed ruleset as this screen shows it: either deployed on the zone (with the rule that
 * deploys it) or available to deploy.
 */
data class ManagedRulesetItem(
    val rulesetId: String,
    val name: String,
    val description: String?,
    /** The id of the execute rule deploying it, or null when it isn't deployed. */
    val deploymentRuleId: String? = null,
    val enabled: Boolean = false,
    val expression: String? = null
) {
    val isDeployed: Boolean get() = deploymentRuleId != null
}

data class ManagedRulesetsUiState(
    val items: UiState<List<ManagedRulesetItem>> = UiState.Loading,
    val isRefreshing: Boolean = false,
    val entrypointId: String? = null,
    val busyRulesetId: String? = null,
    val error: String? = null
)

/**
 * Joins the zone's deployment rules to the catalogue of managed rulesets. A deployment rule
 * carries only the ruleset's id, so without the catalogue the screen would be a list of UUIDs.
 * A managed ruleset that is deployed is listed first, since that's what's actually protecting
 * the zone.
 */
fun buildManagedItems(entrypoint: Ruleset?, catalogue: List<Ruleset>): List<ManagedRulesetItem> {
    val deployments = entrypoint?.rules.orEmpty()
        .filter { it.action == EXECUTE_ACTION }
        .mapNotNull { rule -> rule.actionParameters?.id?.let { it to rule } }
        .toMap()
    val managed = catalogue.filter { it.kind == "managed" && it.phase == MANAGED_WAF_PHASE }
    val items = managed.map { ruleset ->
        val deployment = deployments[ruleset.id]
        ManagedRulesetItem(
            rulesetId = ruleset.id,
            name = ruleset.name ?: ruleset.id,
            description = ruleset.description,
            deploymentRuleId = deployment?.id,
            enabled = deployment?.enabled ?: false,
            expression = deployment?.expression
        )
    }
    // A ruleset deployed on the zone but missing from the catalogue would otherwise vanish
    // from the screen while still filtering traffic.
    val catalogued = managed.map { it.id }.toSet()
    val orphans = deployments.filterKeys { it !in catalogued }.map { (rulesetId, rule) ->
        ManagedRulesetItem(
            rulesetId = rulesetId,
            name = rulesetId,
            description = "Deployed on this zone; Cloudflare didn't list this ruleset",
            deploymentRuleId = rule.id,
            enabled = rule.enabled,
            expression = rule.expression
        )
    }
    return (orphans + items).sortedByDescending { it.isDeployed }
}

class ManagedRulesetsViewModel(
    private val zoneId: String,
    private val repository: RulesetPhaseRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ManagedRulesetsUiState())
    val uiState: StateFlow<ManagedRulesetsUiState> = _uiState.asStateFlow()

    init {
        load(isRefresh = false)
    }

    fun refresh() = load(isRefresh = true)

    /** Both requests run in one coroutine, sequentially: the screen can't render either half
     *  alone, and racing two launches makes the loading state ambiguous. */
    private fun load(isRefresh: Boolean) {
        _uiState.update { if (isRefresh) it.copy(isRefreshing = true) else it.copy(items = UiState.Loading) }
        viewModelScope.launch {
            when (val entrypoint = repository.getRuleset(zoneId, MANAGED_WAF_PHASE)) {
                is ApiResult.Failure -> _uiState.update {
                    it.copy(items = UiState.Error(ErrorClassifier.classify(entrypoint)), isRefreshing = false)
                }
                is ApiResult.Success -> when (val catalogue = repository.listRulesets(zoneId)) {
                    is ApiResult.Failure -> _uiState.update {
                        it.copy(items = UiState.Error(ErrorClassifier.classify(catalogue)), isRefreshing = false)
                    }
                    is ApiResult.Success -> _uiState.update {
                        it.copy(
                            items = UiState.Data(buildManagedItems(entrypoint.data, catalogue.data)),
                            entrypointId = entrypoint.data?.id,
                            isRefreshing = false
                        )
                    }
                }
            }
        }
    }

    /** Deploys a managed ruleset on the zone, or enables/disables one already deployed. */
    fun setDeployed(item: ManagedRulesetItem, deployed: Boolean) {
        _uiState.update { it.copy(busyRulesetId = item.rulesetId, error = null) }
        viewModelScope.launch {
            val entrypointId = _uiState.value.entrypointId
            val write = RulesetRuleWrite(
                action = EXECUTE_ACTION,
                expression = item.expression ?: "true",
                enabled = deployed,
                actionParameters = RuleActionParameters(id = item.rulesetId)
            )
            val result = when {
                item.deploymentRuleId != null && entrypointId != null ->
                    repository.updateRule(zoneId, entrypointId, item.deploymentRuleId, write)
                else -> repository.addRule(zoneId, MANAGED_WAF_PHASE, entrypointId, write)
            }
            when (result) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(busyRulesetId = null, entrypointId = result.data.id) }
                    load(isRefresh = true)
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(busyRulesetId = null, error = result.message)
                }
            }
        }
    }

    /** Removes the deployment entirely, rather than leaving a disabled rule behind. */
    fun undeploy(item: ManagedRulesetItem) {
        val entrypointId = _uiState.value.entrypointId ?: return
        val ruleId = item.deploymentRuleId ?: return
        _uiState.update { it.copy(busyRulesetId = item.rulesetId, error = null) }
        viewModelScope.launch {
            when (val result = repository.deleteRule(zoneId, entrypointId, ruleId)) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(busyRulesetId = null, entrypointId = result.data.id) }
                    load(isRefresh = true)
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(busyRulesetId = null, error = result.message)
                }
            }
        }
    }

    fun dismissError() = _uiState.update { it.copy(error = null) }
}

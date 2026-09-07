package dev.cfmobile.app.ui.magicfirewall

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cfmobile.app.core.errors.ErrorClassifier
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.dto.RulesetRule
import dev.cfmobile.app.data.remote.dto.RulesetRuleWrite
import dev.cfmobile.app.data.repository.MagicFirewallRepository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What Magic Firewall can do with a packet. Unlike the HTTP phases there is no challenge or
 *  redirect here - this filter sees packets, not requests. */
enum class MagicFirewallAction(val value: String, val label: String, val description: String) {
    BLOCK("block", "Block", "Drop matching packets"),
    LOG("log", "Log", "Record matching packets without dropping them"),
    SKIP("skip", "Allow", "Let matching packets through, skipping the rules below")
}

fun actionFromValue(value: String): MagicFirewallAction =
    MagicFirewallAction.entries.firstOrNull { it.value == value } ?: MagicFirewallAction.BLOCK

data class MagicFirewallFormState(
    val editingId: String? = null,
    val description: String = "",
    val expression: String = "",
    val action: MagicFirewallAction = MagicFirewallAction.BLOCK,
    val enabled: Boolean = true,
    val isSaving: Boolean = false,
    val error: String? = null
)

data class MagicFirewallUiState(
    val rules: UiState<List<RulesetRule>> = UiState.Loading,
    val isRefreshing: Boolean = false,
    /** Null until the account has its first rule - saving one creates the ruleset. */
    val rulesetId: String? = null,
    val busyId: String? = null,
    val deletingId: String? = null,
    val form: MagicFirewallFormState? = null,
    val error: String? = null
)

fun validateMagicFirewallForm(form: MagicFirewallFormState): String? {
    if (form.expression.isBlank()) return "An expression is required"
    if (form.description.isBlank()) return "A description is required - it's what identifies the rule later"
    return null
}

fun buildMagicFirewallWrite(form: MagicFirewallFormState) = RulesetRuleWrite(
    action = form.action.value,
    expression = form.expression.trim(),
    description = form.description.trim(),
    enabled = form.enabled
)

/** "Block · enabled" - what the rule does and whether it is doing it. */
fun ruleSummary(rule: RulesetRule): String = listOfNotNull(
    actionFromValue(rule.action).label,
    if (rule.enabled) "enabled" else "disabled"
).joinToString(" · ")

/**
 * Magic Firewall: the packet filter in front of Magic Transit traffic. Same Rulesets engine as
 * the zone rule screens, at account scope, so the shape here matches PhaseRulesViewModel -
 * that base is bound to a zone id, which is why this doesn't extend it.
 */
class MagicFirewallViewModel(
    private val accountId: String,
    private val repository: MagicFirewallRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MagicFirewallUiState())
    val uiState: StateFlow<MagicFirewallUiState> = _uiState.asStateFlow()

    init {
        load(isRefresh = false)
    }

    fun refresh() = load(isRefresh = true)

    private fun load(isRefresh: Boolean) {
        _uiState.update { if (isRefresh) it.copy(isRefreshing = true) else it.copy(rules = UiState.Loading) }
        viewModelScope.launch {
            when (val result = repository.getRuleset(accountId)) {
                // An account that has never held a rule has no ruleset at all, which the
                // repository reports as null rather than as an error.
                is ApiResult.Success -> _uiState.update {
                    it.copy(
                        rules = UiState.Data(result.data?.rules.orEmpty()),
                        rulesetId = result.data?.id,
                        isRefreshing = false
                    )
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(rules = UiState.Error(ErrorClassifier.classify(result)), isRefreshing = false)
                }
            }
        }
    }

    fun openForm() = _uiState.update { it.copy(form = MagicFirewallFormState()) }

    fun editRule(rule: RulesetRule) = _uiState.update {
        it.copy(
            form = MagicFirewallFormState(
                editingId = rule.id,
                description = rule.description.orEmpty(),
                expression = rule.expression,
                action = actionFromValue(rule.action),
                enabled = rule.enabled
            )
        )
    }

    fun closeForm() = _uiState.update { it.copy(form = null) }

    fun updateForm(transform: (MagicFirewallFormState) -> MagicFirewallFormState) =
        _uiState.update { state -> state.form?.let { state.copy(form = transform(it)) } ?: state }

    fun save() {
        val form = _uiState.value.form ?: return
        val validationError = validateMagicFirewallForm(form)
        if (validationError != null) {
            updateForm { it.copy(error = validationError) }
            return
        }
        val rulesetId = _uiState.value.rulesetId
        updateForm { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            val write = buildMagicFirewallWrite(form)
            val result = if (form.editingId != null && rulesetId != null) {
                repository.updateRule(accountId, rulesetId, form.editingId, write)
            } else {
                repository.addRule(accountId, rulesetId, write)
            }
            when (result) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            form = null,
                            rulesetId = result.data.id,
                            rules = UiState.Data(result.data.rules)
                        )
                    }
                }
                is ApiResult.Failure -> updateForm { it.copy(isSaving = false, error = result.message) }
            }
        }
    }

    /** Turning a packet filter off is the change made under pressure, so it's a switch on the
     *  row rather than a trip through the form. */
    fun setEnabled(rule: RulesetRule, enabled: Boolean) {
        val rulesetId = _uiState.value.rulesetId ?: return
        _uiState.update { it.copy(busyId = rule.id, error = null) }
        viewModelScope.launch {
            val write = RulesetRuleWrite(
                action = rule.action,
                expression = rule.expression,
                description = rule.description,
                enabled = enabled,
                actionParameters = rule.actionParameters
            )
            when (val result = repository.updateRule(accountId, rulesetId, rule.id, write)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(busyId = null, rules = UiState.Data(result.data.rules))
                }
                is ApiResult.Failure -> _uiState.update { it.copy(busyId = null, error = result.message) }
            }
        }
    }

    fun delete(rule: RulesetRule) {
        val rulesetId = _uiState.value.rulesetId ?: return
        _uiState.update { it.copy(deletingId = rule.id, error = null) }
        viewModelScope.launch {
            when (val result = repository.deleteRule(accountId, rulesetId, rule.id)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(deletingId = null, rules = UiState.Data(result.data.rules))
                }
                is ApiResult.Failure -> _uiState.update { it.copy(deletingId = null, error = result.message) }
            }
        }
    }

    fun dismissError() = _uiState.update { it.copy(error = null) }
}

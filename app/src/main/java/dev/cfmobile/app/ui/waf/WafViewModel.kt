package dev.cfmobile.app.ui.waf

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cfmobile.app.core.errors.ErrorClassifier
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.dto.RulesetRule
import dev.cfmobile.app.data.remote.dto.RulesetRuleWrite
import dev.cfmobile.app.data.repository.WafRepository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

val WAF_RULE_ACTIONS = listOf("block", "challenge", "js_challenge", "managed_challenge", "log", "skip")

data class WafRuleForm(
    val editingId: String? = null,
    val expression: String = "",
    val action: String = "block",
    val description: String = "",
    val enabled: Boolean = true,
    val isSaving: Boolean = false,
    val error: String? = null
)

data class WafUiState(
    val rules: UiState<List<RulesetRule>> = UiState.Loading,
    val rulesetId: String? = null,
    val form: WafRuleForm? = null,
    val deletingId: String? = null
)

class WafViewModel(
    private val zoneId: String,
    private val repository: WafRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(WafUiState())
    val uiState: StateFlow<WafUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _uiState.update { it.copy(rules = UiState.Loading) }
        viewModelScope.launch {
            when (val result = repository.getCustomRuleset(zoneId)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(rules = UiState.Data(result.data?.rules ?: emptyList()), rulesetId = result.data?.id)
                }
                is ApiResult.Failure -> _uiState.update { it.copy(rules = UiState.Error(ErrorClassifier.classify(result))) }
            }
        }
    }

    fun openAddForm() = _uiState.update { it.copy(form = WafRuleForm()) }

    fun openEditForm(rule: RulesetRule) = _uiState.update {
        it.copy(
            form = WafRuleForm(
                editingId = rule.id,
                expression = rule.expression,
                action = rule.action,
                description = rule.description ?: "",
                enabled = rule.enabled
            )
        )
    }

    fun closeForm() = _uiState.update { it.copy(form = null) }

    fun updateForm(transform: (WafRuleForm) -> WafRuleForm) =
        _uiState.update { state -> state.form?.let { state.copy(form = transform(it)) } ?: state }

    fun save() {
        val form = _uiState.value.form ?: return
        if (form.expression.isBlank()) {
            updateForm { it.copy(error = "Expression is required") }
            return
        }
        updateForm { it.copy(isSaving = true, error = null) }
        val write = RulesetRuleWrite(
            action = form.action,
            expression = form.expression.trim(),
            description = form.description.trim().ifBlank { null },
            enabled = form.enabled
        )

        viewModelScope.launch {
            val rulesetId = _uiState.value.rulesetId
            val result = if (form.editingId != null && rulesetId != null) {
                repository.updateRule(zoneId, rulesetId, form.editingId, write)
            } else {
                repository.addRule(zoneId, rulesetId, write)
            }
            when (result) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(form = null, rulesetId = result.data.id, rules = UiState.Data(result.data.rules))
                }
                is ApiResult.Failure -> updateForm { it.copy(isSaving = false, error = result.message) }
            }
        }
    }

    /** Flips just `enabled` without opening the edit form - the common "pause this rule for
     *  now" action shouldn't require re-entering its expression and action. */
    fun toggleEnabled(rule: RulesetRule) {
        val rulesetId = _uiState.value.rulesetId ?: return
        viewModelScope.launch {
            val write = RulesetRuleWrite(action = rule.action, expression = rule.expression, description = rule.description, enabled = !rule.enabled)
            when (val result = repository.updateRule(zoneId, rulesetId, rule.id, write)) {
                is ApiResult.Success -> _uiState.update { it.copy(rules = UiState.Data(result.data.rules)) }
                is ApiResult.Failure -> _uiState.update { it.copy(rules = UiState.Error(ErrorClassifier.classify(result))) }
            }
        }
    }

    fun delete(rule: RulesetRule) {
        val rulesetId = _uiState.value.rulesetId ?: return
        _uiState.update { it.copy(deletingId = rule.id) }
        viewModelScope.launch {
            val result = repository.deleteRule(zoneId, rulesetId, rule.id)
            _uiState.update { it.copy(deletingId = null) }
            when (result) {
                is ApiResult.Success -> _uiState.update { it.copy(rules = UiState.Data(result.data.rules)) }
                is ApiResult.Failure -> _uiState.update { it.copy(rules = UiState.Error(ErrorClassifier.classify(result))) }
            }
        }
    }
}

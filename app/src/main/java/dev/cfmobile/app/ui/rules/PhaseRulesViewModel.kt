package dev.cfmobile.app.ui.rules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cfmobile.app.core.errors.ErrorClassifier
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.dto.RulesetRule
import dev.cfmobile.app.data.remote.dto.RulesetRuleWrite
import dev.cfmobile.app.data.repository.RulesetPhaseRepository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The editor state for one rules-engine family. The recursive type parameter is what lets
 * [PhaseRulesViewModel] flip the saving flag and set an error without knowing anything else
 * about a particular family's form.
 */
interface PhaseRuleForm<F : PhaseRuleForm<F>> {
    /** Null while adding a rule, set to the rule's id while editing one. */
    val editingId: String?
    val isSaving: Boolean
    val error: String?
    fun withStatus(isSaving: Boolean, error: String?): F
}

data class PhaseRulesUiState<F : PhaseRuleForm<F>>(
    val rules: UiState<List<RulesetRule>> = UiState.Loading,
    val isRefreshing: Boolean = false,
    /** Null until the phase has an entrypoint ruleset - the first rule creates it. */
    val rulesetId: String? = null,
    val deletingId: String? = null,
    val form: F? = null
)

/**
 * Shared behaviour for every screen that edits the rules of a single Rulesets phase: Redirect
 * Rules, Origin Rules, and Cache Rules all differ only in the phase they write to and the
 * action_parameters they build, so the list/create/edit/delete cycle lives here once.
 *
 * Transform Rules predate this and stay on their own ViewModel: they span three phases at once
 * behind a tab bar, which is a different shape from a single-phase screen.
 */
abstract class PhaseRulesViewModel<F : PhaseRuleForm<F>>(
    private val zoneId: String,
    private val phase: String,
    private val repository: RulesetPhaseRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PhaseRulesUiState<F>())
    val uiState: StateFlow<PhaseRulesUiState<F>> = _uiState.asStateFlow()

    /** Rejects a form before any request is made; null means it's good to send. */
    protected abstract fun validate(form: F): String?

    /** Turns a valid form into the rule Cloudflare should store. */
    protected abstract fun buildWrite(form: F): RulesetRuleWrite

    init {
        load(isRefresh = false)
    }

    fun refresh() = load(isRefresh = true)

    private fun load(isRefresh: Boolean) {
        _uiState.update { if (isRefresh) it.copy(isRefreshing = true) else it.copy(rules = UiState.Loading) }
        viewModelScope.launch {
            when (val result = repository.getRuleset(zoneId, phase)) {
                // A phase that has never held a rule has no ruleset at all, which the
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

    protected fun showForm(form: F) = _uiState.update { it.copy(form = form) }

    fun closeForm() = _uiState.update { it.copy(form = null) }

    fun updateForm(transform: (F) -> F) =
        _uiState.update { state -> state.form?.let { state.copy(form = transform(it)) } ?: state }

    fun save() {
        val form = _uiState.value.form ?: return
        val validationError = validate(form)
        if (validationError != null) {
            updateForm { it.withStatus(isSaving = false, error = validationError) }
            return
        }
        updateForm { it.withStatus(isSaving = true, error = null) }
        viewModelScope.launch {
            val write = buildWrite(form)
            val rulesetId = _uiState.value.rulesetId
            val editingId = form.editingId
            val result = if (editingId != null && rulesetId != null) {
                repository.updateRule(zoneId, rulesetId, editingId, write)
            } else {
                repository.addRule(zoneId, phase, rulesetId, write)
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
                is ApiResult.Failure -> updateForm { it.withStatus(isSaving = false, error = result.message) }
            }
        }
    }

    fun delete(rule: RulesetRule) {
        val rulesetId = _uiState.value.rulesetId ?: return
        _uiState.update { it.copy(deletingId = rule.id) }
        viewModelScope.launch {
            // Cloudflare answers a rule change with the whole updated ruleset, so the list is
            // taken from the response rather than re-fetched.
            when (val result = repository.deleteRule(zoneId, rulesetId, rule.id)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(deletingId = null, rules = UiState.Data(result.data.rules))
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(deletingId = null, rules = UiState.Error(ErrorClassifier.classify(result)))
                }
            }
        }
    }

    /** Enables or disables a rule in place, leaving everything else about it untouched. */
    fun setEnabled(rule: RulesetRule, enabled: Boolean) {
        val rulesetId = _uiState.value.rulesetId ?: return
        viewModelScope.launch {
            val write = RulesetRuleWrite(
                action = rule.action,
                expression = rule.expression,
                description = rule.description,
                enabled = enabled,
                actionParameters = rule.actionParameters
            )
            when (val result = repository.updateRule(zoneId, rulesetId, rule.id, write)) {
                is ApiResult.Success -> _uiState.update { it.copy(rules = UiState.Data(result.data.rules)) }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(rules = UiState.Error(ErrorClassifier.classify(result)))
                }
            }
        }
    }
}

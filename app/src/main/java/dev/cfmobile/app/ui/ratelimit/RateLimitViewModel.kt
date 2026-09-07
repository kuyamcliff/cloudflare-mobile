package dev.cfmobile.app.ui.ratelimit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cfmobile.app.core.errors.ErrorClassifier
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.dto.RateLimit
import dev.cfmobile.app.data.remote.dto.RulesetRule
import dev.cfmobile.app.data.remote.dto.RulesetRuleWrite
import dev.cfmobile.app.data.repository.RateLimitRepository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Cloudflare rejects any period outside this fixed set - a free-text field would let a user
 *  build a request that's guaranteed to fail. */
val RATE_LIMIT_PERIODS = listOf(10, 60, 600, 3600, 86400)

/** "skip" (bypass other rules) doesn't apply to rate limiting the way it does to WAF Custom
 *  Rules, so this list is deliberately narrower than WAF_RULE_ACTIONS. */
val RATE_LIMIT_ACTIONS = listOf("block", "challenge", "js_challenge", "managed_challenge", "log")

data class RateLimitRuleForm(
    val editingId: String? = null,
    val expression: String = "",
    val action: String = "block",
    val description: String = "",
    val enabled: Boolean = true,
    val period: Int = 60,
    val requestsPerPeriod: String = "100",
    val mitigationTimeout: String = "",
    val isSaving: Boolean = false,
    val error: String? = null
)

data class RateLimitUiState(
    val rules: UiState<List<RulesetRule>> = UiState.Loading,
    val rulesetId: String? = null,
    val form: RateLimitRuleForm? = null,
    val deletingId: String? = null
)

/** Pure so the payload sent for a rate limiting rule is directly testable. */
fun validateRateLimitForm(form: RateLimitRuleForm): String? = when {
    form.expression.isBlank() -> "Expression is required"
    form.requestsPerPeriod.toIntOrNull()?.let { it > 0 } != true -> "Requests per period must be a positive number"
    form.mitigationTimeout.isNotBlank() && form.mitigationTimeout.toIntOrNull() == null -> "Mitigation timeout must be a number"
    else -> null
}

fun buildRateLimitRuleWrite(form: RateLimitRuleForm): RulesetRuleWrite = RulesetRuleWrite(
    action = form.action,
    expression = form.expression.trim(),
    description = form.description.trim().ifBlank { null },
    enabled = form.enabled,
    ratelimit = RateLimit(
        characteristics = listOf("ip.src"),
        period = form.period,
        requestsPerPeriod = form.requestsPerPeriod.toIntOrNull() ?: 100,
        mitigationTimeout = form.mitigationTimeout.toIntOrNull()
    )
)

class RateLimitViewModel(
    private val zoneId: String,
    private val repository: RateLimitRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RateLimitUiState())
    val uiState: StateFlow<RateLimitUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _uiState.update { it.copy(rules = UiState.Loading) }
        viewModelScope.launch {
            when (val result = repository.getRuleset(zoneId)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(rules = UiState.Data(result.data?.rules ?: emptyList()), rulesetId = result.data?.id)
                }
                is ApiResult.Failure -> _uiState.update { it.copy(rules = UiState.Error(ErrorClassifier.classify(result))) }
            }
        }
    }

    fun openAddForm() = _uiState.update { it.copy(form = RateLimitRuleForm()) }

    fun openEditForm(rule: RulesetRule) = _uiState.update {
        val rl = rule.ratelimit
        it.copy(
            form = RateLimitRuleForm(
                editingId = rule.id,
                expression = rule.expression,
                action = rule.action,
                description = rule.description ?: "",
                enabled = rule.enabled,
                period = rl?.period ?: 60,
                requestsPerPeriod = (rl?.requestsPerPeriod ?: 100).toString(),
                mitigationTimeout = rl?.mitigationTimeout?.toString() ?: ""
            )
        )
    }

    fun closeForm() = _uiState.update { it.copy(form = null) }

    fun updateForm(transform: (RateLimitRuleForm) -> RateLimitRuleForm) =
        _uiState.update { state -> state.form?.let { state.copy(form = transform(it)) } ?: state }

    fun save() {
        val form = _uiState.value.form ?: return
        val validationError = validateRateLimitForm(form)
        if (validationError != null) {
            updateForm { it.copy(error = validationError) }
            return
        }
        updateForm { it.copy(isSaving = true, error = null) }
        val write = buildRateLimitRuleWrite(form)

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

    fun toggleEnabled(rule: RulesetRule) {
        val rulesetId = _uiState.value.rulesetId ?: return
        viewModelScope.launch {
            val write = RulesetRuleWrite(
                action = rule.action, expression = rule.expression, description = rule.description,
                enabled = !rule.enabled, ratelimit = rule.ratelimit
            )
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

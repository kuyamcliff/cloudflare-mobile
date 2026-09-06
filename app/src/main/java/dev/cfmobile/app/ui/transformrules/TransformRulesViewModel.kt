package dev.cfmobile.app.ui.transformrules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cfmobile.app.core.errors.ErrorClassifier
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.dto.HeaderModification
import dev.cfmobile.app.data.remote.dto.RulesetRule
import dev.cfmobile.app.data.remote.dto.RulesetRuleWrite
import dev.cfmobile.app.data.remote.dto.RuleActionParameters
import dev.cfmobile.app.data.remote.dto.UriRewrite
import dev.cfmobile.app.data.remote.dto.UriRewritePart
import dev.cfmobile.app.data.repository.RulesetPhaseRepository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Transform Rules cover three independent Rulesets phases that all share the single
 *  "rewrite" action - which of a rule's action_parameters is populated (uri vs headers)
 *  is what actually distinguishes them. */
enum class TransformRuleKind(val phase: String, val label: String) {
    URL_REWRITE("http_request_transform", "URL Rewrite"),
    REQUEST_HEADERS("http_request_late_transform", "Request Headers"),
    RESPONSE_HEADERS("http_response_headers_transform", "Response Headers")
}

const val TRANSFORM_ACTION = "rewrite"
val HEADER_OPERATIONS = listOf("set", "remove")

data class TransformKindState(
    val rules: UiState<List<RulesetRule>> = UiState.Loading,
    val rulesetId: String? = null,
    val deletingId: String? = null,
    val loaded: Boolean = false
)

data class TransformRuleForm(
    val kind: TransformRuleKind,
    val editingId: String? = null,
    val expression: String = "true",
    val description: String = "",
    val enabled: Boolean = true,
    val pathValue: String = "",
    val pathIsExpression: Boolean = false,
    val queryValue: String = "",
    val queryIsExpression: Boolean = false,
    val headerName: String = "",
    val headerOperation: String = "set",
    val headerValue: String = "",
    val headerIsExpression: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null
)

data class TransformRulesUiState(
    val selectedKind: TransformRuleKind = TransformRuleKind.URL_REWRITE,
    val states: Map<TransformRuleKind, TransformKindState> = TransformRuleKind.entries.associateWith { TransformKindState() },
    val form: TransformRuleForm? = null
) {
    val activeState: TransformKindState get() = states.getValue(selectedKind)
}

fun validateTransformForm(form: TransformRuleForm): String? {
    if (form.expression.isBlank()) return "Expression is required"
    return when (form.kind) {
        TransformRuleKind.URL_REWRITE ->
            if (form.pathValue.isBlank() && form.queryValue.isBlank()) "Set a path and/or query rewrite" else null
        TransformRuleKind.REQUEST_HEADERS, TransformRuleKind.RESPONSE_HEADERS -> when {
            form.headerName.isBlank() -> "Header name is required"
            form.headerOperation == "set" && form.headerValue.isBlank() -> "A value or expression is required to set a header"
            else -> null
        }
    }
}

/** Pure so the action_parameters payload for each Transform Rule kind is directly testable. */
fun buildTransformRuleWrite(form: TransformRuleForm): RulesetRuleWrite {
    val actionParameters = when (form.kind) {
        TransformRuleKind.URL_REWRITE -> RuleActionParameters(
            uri = UriRewrite(
                path = form.pathValue.takeIf { it.isNotBlank() }?.let { rewritePart(it, form.pathIsExpression) },
                query = form.queryValue.takeIf { it.isNotBlank() }?.let { rewritePart(it, form.queryIsExpression) }
            )
        )
        TransformRuleKind.REQUEST_HEADERS, TransformRuleKind.RESPONSE_HEADERS -> RuleActionParameters(
            headers = mapOf(
                form.headerName.trim() to if (form.headerOperation == "remove") {
                    HeaderModification(operation = "remove")
                } else {
                    HeaderModification(
                        operation = "set",
                        value = if (!form.headerIsExpression) form.headerValue.trim() else null,
                        expression = if (form.headerIsExpression) form.headerValue.trim() else null
                    )
                }
            )
        )
    }
    return RulesetRuleWrite(
        action = TRANSFORM_ACTION,
        expression = form.expression.trim(),
        description = form.description.trim().ifBlank { null },
        enabled = form.enabled,
        actionParameters = actionParameters
    )
}

private fun rewritePart(value: String, isExpression: Boolean): UriRewritePart =
    if (isExpression) UriRewritePart(expression = value.trim()) else UriRewritePart(value = value.trim())

class TransformRulesViewModel(
    private val zoneId: String,
    private val repository: RulesetPhaseRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TransformRulesUiState())
    val uiState: StateFlow<TransformRulesUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun selectKind(kind: TransformRuleKind) {
        _uiState.update { it.copy(selectedKind = kind) }
        if (_uiState.value.states.getValue(kind).loaded.not()) refresh()
    }

    fun refresh() {
        val kind = _uiState.value.selectedKind
        updateKindState(kind) { it.copy(rules = UiState.Loading) }
        viewModelScope.launch {
            when (val result = repository.getRuleset(zoneId, kind.phase)) {
                is ApiResult.Success -> updateKindState(kind) {
                    it.copy(rules = UiState.Data(result.data?.rules ?: emptyList()), rulesetId = result.data?.id, loaded = true)
                }
                is ApiResult.Failure -> updateKindState(kind) {
                    it.copy(rules = UiState.Error(ErrorClassifier.classify(result)), loaded = true)
                }
            }
        }
    }

    fun openAddForm() = _uiState.update { it.copy(form = TransformRuleForm(kind = it.selectedKind)) }

    fun openEditForm(rule: RulesetRule) = _uiState.update { state ->
        val ap = rule.actionParameters
        state.copy(
            form = TransformRuleForm(
                kind = state.selectedKind,
                editingId = rule.id,
                expression = rule.expression,
                description = rule.description ?: "",
                enabled = rule.enabled,
                pathValue = ap?.uri?.path?.value ?: ap?.uri?.path?.expression ?: "",
                pathIsExpression = ap?.uri?.path?.expression != null,
                queryValue = ap?.uri?.query?.value ?: ap?.uri?.query?.expression ?: "",
                queryIsExpression = ap?.uri?.query?.expression != null,
                headerName = ap?.headers?.keys?.firstOrNull() ?: "",
                headerOperation = ap?.headers?.values?.firstOrNull()?.operation ?: "set",
                headerValue = ap?.headers?.values?.firstOrNull()?.let { it.value ?: it.expression } ?: "",
                headerIsExpression = ap?.headers?.values?.firstOrNull()?.expression != null
            )
        )
    }

    fun closeForm() = _uiState.update { it.copy(form = null) }

    fun updateForm(transform: (TransformRuleForm) -> TransformRuleForm) =
        _uiState.update { state -> state.form?.let { state.copy(form = transform(it)) } ?: state }

    fun save() {
        val form = _uiState.value.form ?: return
        val validationError = validateTransformForm(form)
        if (validationError != null) {
            updateForm { it.copy(error = validationError) }
            return
        }
        updateForm { it.copy(isSaving = true, error = null) }
        val write = buildTransformRuleWrite(form)
        val kind = form.kind

        viewModelScope.launch {
            val rulesetId = _uiState.value.states.getValue(kind).rulesetId
            val result = if (form.editingId != null && rulesetId != null) {
                repository.updateRule(zoneId, rulesetId, form.editingId, write)
            } else {
                repository.addRule(zoneId, kind.phase, rulesetId, write)
            }
            when (result) {
                is ApiResult.Success -> {
                    updateKindState(kind) { it.copy(rulesetId = result.data.id, rules = UiState.Data(result.data.rules)) }
                    _uiState.update { it.copy(form = null) }
                }
                is ApiResult.Failure -> updateForm { it.copy(isSaving = false, error = result.message) }
            }
        }
    }

    fun toggleEnabled(rule: RulesetRule) {
        val kind = _uiState.value.selectedKind
        val rulesetId = _uiState.value.states.getValue(kind).rulesetId ?: return
        viewModelScope.launch {
            val write = RulesetRuleWrite(
                action = rule.action, expression = rule.expression, description = rule.description,
                enabled = !rule.enabled, actionParameters = rule.actionParameters
            )
            when (val result = repository.updateRule(zoneId, rulesetId, rule.id, write)) {
                is ApiResult.Success -> updateKindState(kind) { it.copy(rules = UiState.Data(result.data.rules)) }
                is ApiResult.Failure -> updateKindState(kind) { it.copy(rules = UiState.Error(ErrorClassifier.classify(result))) }
            }
        }
    }

    fun delete(rule: RulesetRule) {
        val kind = _uiState.value.selectedKind
        val rulesetId = _uiState.value.states.getValue(kind).rulesetId ?: return
        updateKindState(kind) { it.copy(deletingId = rule.id) }
        viewModelScope.launch {
            val result = repository.deleteRule(zoneId, rulesetId, rule.id)
            updateKindState(kind) { it.copy(deletingId = null) }
            when (result) {
                is ApiResult.Success -> updateKindState(kind) { it.copy(rules = UiState.Data(result.data.rules)) }
                is ApiResult.Failure -> updateKindState(kind) { it.copy(rules = UiState.Error(ErrorClassifier.classify(result))) }
            }
        }
    }

    private fun updateKindState(kind: TransformRuleKind, transform: (TransformKindState) -> TransformKindState) {
        _uiState.update { it.copy(states = it.states + (kind to transform(it.states.getValue(kind)))) }
    }
}

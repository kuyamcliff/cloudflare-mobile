package dev.cfmobile.app.ui.pagerules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cfmobile.app.core.errors.ErrorClassifier
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.dto.PageRule
import dev.cfmobile.app.data.repository.PageRulesRepository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Actions this app exposes for a new page rule. Cloudflare supports many more; these cover
 *  the most common per-URL overrides without cluttering the form with a huge action picker. */
enum class PageRuleActionKind(val id: String, val label: String, val takesStringValue: Boolean, val options: List<String> = emptyList()) {
    ALWAYS_USE_HTTPS("always_use_https", "Always Use HTTPS", takesStringValue = false),
    SSL("ssl", "SSL mode", takesStringValue = true, options = listOf("off", "flexible", "full", "strict")),
    CACHE_LEVEL("cache_level", "Cache level", takesStringValue = true, options = listOf("bypass", "basic", "simplified", "aggressive", "cache_everything")),
    DISABLE_SECURITY("disable_security", "Disable Security", takesStringValue = false)
}

data class PageRuleForm(
    val urlPattern: String = "",
    val actionKind: PageRuleActionKind = PageRuleActionKind.ALWAYS_USE_HTTPS,
    val actionValue: String = "",
    val isSaving: Boolean = false,
    val error: String? = null
)

data class PageRulesUiState(
    val rules: UiState<List<PageRule>> = UiState.Loading,
    val form: PageRuleForm? = null
)

class PageRulesViewModel(
    private val zoneId: String,
    private val repository: PageRulesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PageRulesUiState())
    val uiState: StateFlow<PageRulesUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _uiState.update { it.copy(rules = UiState.Loading) }
        viewModelScope.launch {
            _uiState.update {
                it.copy(rules = when (val r = repository.listRules(zoneId)) {
                    is ApiResult.Success -> UiState.Data(r.data.sortedBy(PageRule::priority))
                    is ApiResult.Failure -> UiState.Error(ErrorClassifier.classify(r))
                })
            }
        }
    }

    fun openForm() = _uiState.update { it.copy(form = PageRuleForm()) }
    fun closeForm() = _uiState.update { it.copy(form = null) }
    fun updateForm(transform: (PageRuleForm) -> PageRuleForm) =
        _uiState.update { state -> state.form?.let { state.copy(form = transform(it)) } ?: state }

    fun save() {
        val form = _uiState.value.form ?: return
        if (form.urlPattern.isBlank()) {
            updateForm { it.copy(error = "URL pattern is required") }
            return
        }
        val value: Any? = if (form.actionKind.takesStringValue) form.actionValue.ifBlank { form.actionKind.options.first() } else "on"
        updateForm { it.copy(isSaving = true, error = null) }
        val nextPriority = ((_uiState.value.rules as? UiState.Data)?.value?.maxOfOrNull(PageRule::priority) ?: 0) + 1
        viewModelScope.launch {
            when (val result = repository.createRule(zoneId, form.urlPattern.trim(), form.actionKind.id, value, nextPriority)) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(form = null) }
                    refresh()
                }
                is ApiResult.Failure -> updateForm { it.copy(isSaving = false, error = result.message) }
            }
        }
    }

    fun toggleActive(rule: PageRule) {
        viewModelScope.launch {
            repository.setStatus(zoneId, rule, active = rule.status != "active")
            refresh()
        }
    }

    fun delete(rule: PageRule) {
        viewModelScope.launch {
            repository.deleteRule(zoneId, rule.id)
            refresh()
        }
    }
}

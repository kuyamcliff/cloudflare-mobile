package dev.cfmobile.app.ui.zoneproducts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cfmobile.app.core.errors.ErrorClassifier
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.dto.Snippet
import dev.cfmobile.app.data.remote.dto.SnippetRule
import dev.cfmobile.app.data.repository.SnippetsRepository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SnippetsTab(val label: String) {
    SNIPPETS("Snippets"),
    RULES("Rules")
}

data class SnippetSourceState(
    val snippet: Snippet,
    val source: UiState<String> = UiState.Loading
)

data class SnippetsUiState(
    val tab: SnippetsTab = SnippetsTab.SNIPPETS,
    val snippets: UiState<List<Snippet>> = UiState.Loading,
    val rules: UiState<List<SnippetRule>> = UiState.Loading,
    val isRefreshing: Boolean = false,
    val source: SnippetSourceState? = null,
    val busyId: String? = null,
    val error: String? = null
)

/** Which snippet a rule runs, and on what - the two things a rule row has to say. */
fun snippetRuleSummary(rule: SnippetRule): String = rule.snippetName.ifBlank { "No snippet" }

class SnippetsViewModel(
    private val zoneId: String,
    private val repository: SnippetsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SnippetsUiState())
    val uiState: StateFlow<SnippetsUiState> = _uiState.asStateFlow()

    init {
        load(isRefresh = false)
    }

    fun refresh() = load(isRefresh = true)

    fun selectTab(tab: SnippetsTab) = _uiState.update { it.copy(tab = tab) }

    /** Snippets and their rules load sequentially in one coroutine - the tabs share a refresh,
     *  and racing two launches would make the refreshing flag ambiguous. */
    private fun load(isRefresh: Boolean) {
        _uiState.update {
            if (isRefresh) it.copy(isRefreshing = true)
            else it.copy(snippets = UiState.Loading, rules = UiState.Loading)
        }
        viewModelScope.launch {
            when (val snippets = repository.listSnippets(zoneId)) {
                is ApiResult.Success -> _uiState.update { it.copy(snippets = UiState.Data(snippets.data)) }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(snippets = UiState.Error(ErrorClassifier.classify(snippets)))
                }
            }
            when (val rules = repository.listRules(zoneId)) {
                is ApiResult.Success -> _uiState.update { it.copy(rules = UiState.Data(rules.data), isRefreshing = false) }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(rules = UiState.Error(ErrorClassifier.classify(rules)), isRefreshing = false)
                }
            }
        }
    }

    /** Opens a snippet's source, which the list response doesn't carry. */
    fun openSource(snippet: Snippet) {
        _uiState.update { it.copy(source = SnippetSourceState(snippet = snippet)) }
        viewModelScope.launch {
            val result = repository.getContent(zoneId, snippet.snippetName)
            _uiState.update { state ->
                val source = state.source
                if (source?.snippet?.snippetName != snippet.snippetName) return@update state
                state.copy(
                    source = source.copy(
                        source = when (result) {
                            is ApiResult.Success -> UiState.Data(result.data)
                            is ApiResult.Failure -> UiState.Error(ErrorClassifier.classify(result))
                        }
                    )
                )
            }
        }
    }

    fun closeSource() = _uiState.update { it.copy(source = null) }

    fun delete(snippet: Snippet) {
        _uiState.update { it.copy(busyId = snippet.snippetName, error = null) }
        viewModelScope.launch {
            when (val result = repository.deleteSnippet(zoneId, snippet.snippetName)) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(busyId = null, source = null) }
                    load(isRefresh = true)
                }
                is ApiResult.Failure -> _uiState.update { it.copy(busyId = null, error = result.message) }
            }
        }
    }

    /**
     * Enables or disables one rule. Cloudflare replaces the zone's whole rule list on write, so
     * the current list is sent back with just this rule changed - which is also why a stale
     * list would silently drop rules, and the response is used as the new truth.
     */
    fun setRuleEnabled(rule: SnippetRule, enabled: Boolean) {
        val current = (_uiState.value.rules as? UiState.Data)?.value ?: return
        val ruleId = rule.id ?: return
        _uiState.update { it.copy(busyId = ruleId, error = null) }
        viewModelScope.launch {
            val updated = current.map { if (it.id == ruleId) it.copy(enabled = enabled) else it }
            when (val result = repository.putRules(zoneId, updated)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(busyId = null, rules = UiState.Data(result.data))
                }
                is ApiResult.Failure -> _uiState.update { it.copy(busyId = null, error = result.message) }
            }
        }
    }

    fun deleteRule(rule: SnippetRule) {
        val current = (_uiState.value.rules as? UiState.Data)?.value ?: return
        val ruleId = rule.id ?: return
        _uiState.update { it.copy(busyId = ruleId, error = null) }
        viewModelScope.launch {
            when (val result = repository.putRules(zoneId, current.filterNot { it.id == ruleId })) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(busyId = null, rules = UiState.Data(result.data))
                }
                is ApiResult.Failure -> _uiState.update { it.copy(busyId = null, error = result.message) }
            }
        }
    }

    fun dismissError() = _uiState.update { it.copy(error = null) }
}

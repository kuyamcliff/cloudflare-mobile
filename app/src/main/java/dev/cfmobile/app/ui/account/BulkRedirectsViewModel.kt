package dev.cfmobile.app.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cfmobile.app.core.errors.ErrorClassifier
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.dto.RulesList
import dev.cfmobile.app.data.remote.dto.RulesListItem
import dev.cfmobile.app.data.repository.BulkRedirectsRepository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Bulk Redirects are the Rules Lists of kind "redirect". */
const val REDIRECT_LIST_KIND = "redirect"

data class BulkRedirectFormState(
    val name: String = "",
    val description: String = "",
    val isSaving: Boolean = false,
    val error: String? = null
)

data class BulkRedirectDetail(
    val list: RulesList,
    val items: UiState<List<RulesListItem>> = UiState.Loading
)

data class BulkRedirectsUiState(
    val lists: UiState<List<RulesList>> = UiState.Loading,
    val isRefreshing: Boolean = false,
    val form: BulkRedirectFormState? = null,
    val detail: BulkRedirectDetail? = null,
    val deletingId: String? = null
)

/** Cloudflare requires a list name to be lowercase letters, digits, and underscores. */
private val LIST_NAME_REGEX = Regex("^[a-z0-9_]+$")

fun validateBulkRedirectForm(form: BulkRedirectFormState): String? = when {
    form.name.isBlank() -> "List name is required"
    !form.name.trim().matches(LIST_NAME_REGEX) ->
        "Use lowercase letters, digits, and underscores only - Cloudflare rejects anything else"
    else -> null
}

/** "example.com/old → https://example.com/new (301)", from whichever fields the item carries. */
fun redirectItemSummary(item: RulesListItem): String {
    val redirect = item.redirect ?: return item.id
    val code = redirect.statusCode?.let { " ($it)" }.orEmpty()
    return "${redirect.sourceUrl} → ${redirect.targetUrl}$code"
}

fun bulkRedirectSubtitle(list: RulesList): String {
    val count = list.numItems ?: 0
    return "$count ${if (count == 1) "redirect" else "redirects"}"
}

class BulkRedirectsViewModel(
    private val accountId: String,
    private val repository: BulkRedirectsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BulkRedirectsUiState())
    val uiState: StateFlow<BulkRedirectsUiState> = _uiState.asStateFlow()

    init {
        load(isRefresh = false)
    }

    fun refresh() = load(isRefresh = true)

    private fun load(isRefresh: Boolean) {
        _uiState.update { if (isRefresh) it.copy(isRefreshing = true) else it.copy(lists = UiState.Loading) }
        viewModelScope.launch {
            when (val result = repository.listLists(accountId)) {
                // The same endpoint serves IP and hostname lists used by WAF rules; only the
                // redirect ones belong on this screen.
                is ApiResult.Success -> _uiState.update {
                    it.copy(
                        lists = UiState.Data(result.data.filter { list -> list.kind == REDIRECT_LIST_KIND }),
                        isRefreshing = false
                    )
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(lists = UiState.Error(ErrorClassifier.classify(result)), isRefreshing = false)
                }
            }
        }
    }

    fun openForm() = _uiState.update { it.copy(form = BulkRedirectFormState()) }

    fun closeForm() = _uiState.update { it.copy(form = null) }

    fun updateForm(transform: (BulkRedirectFormState) -> BulkRedirectFormState) =
        _uiState.update { state -> state.form?.let { state.copy(form = transform(it)) } ?: state }

    fun openDetail(list: RulesList) {
        _uiState.update { it.copy(detail = BulkRedirectDetail(list = list)) }
        viewModelScope.launch {
            val items = repository.listItems(accountId, list.id)
            _uiState.update { state ->
                val detail = state.detail
                if (detail?.list?.id != list.id) return@update state
                state.copy(
                    detail = detail.copy(
                        items = when (items) {
                            is ApiResult.Success -> UiState.Data(items.data)
                            is ApiResult.Failure -> UiState.Error(ErrorClassifier.classify(items))
                        }
                    )
                )
            }
        }
    }

    fun closeDetail() = _uiState.update { it.copy(detail = null) }

    fun save() {
        val form = _uiState.value.form ?: return
        val validationError = validateBulkRedirectForm(form)
        if (validationError != null) {
            updateForm { it.copy(error = validationError) }
            return
        }
        updateForm { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            val result = repository.createList(
                accountId,
                name = form.name.trim(),
                kind = REDIRECT_LIST_KIND,
                description = form.description.trim().ifBlank { null }
            )
            when (result) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(form = null) }
                    load(isRefresh = true)
                }
                is ApiResult.Failure -> updateForm { it.copy(isSaving = false, error = result.message) }
            }
        }
    }

    fun delete(list: RulesList) {
        _uiState.update { it.copy(deletingId = list.id) }
        viewModelScope.launch {
            repository.deleteList(accountId, list.id)
            _uiState.update { it.copy(deletingId = null, detail = null) }
            load(isRefresh = true)
        }
    }
}

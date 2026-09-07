package dev.cfmobile.app.ui.zerotrust

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cfmobile.app.core.errors.ErrorClassifier
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.dto.GatewayList
import dev.cfmobile.app.data.remote.dto.GatewayListCreate
import dev.cfmobile.app.data.remote.dto.GatewayListItem
import dev.cfmobile.app.data.repository.GatewayRepository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The list types Gateway policies can match against. */
enum class GatewayListType(val apiValue: String, val label: String, val placeholder: String) {
    DOMAIN("DOMAIN", "Domains", "example.com"),
    URL("URL", "URLs", "example.com/path"),
    IP("IP", "IP addresses", "203.0.113.10"),
    SERIAL("SERIAL", "Device serials", "C02XY1234"),
    EMAIL("EMAIL", "Emails", "user@example.com")
}

data class GatewayListFormState(
    val name: String = "",
    val description: String = "",
    val type: GatewayListType = GatewayListType.DOMAIN,
    /** One entry per line - a list is bulk data, and typing them one field at a time on a
     *  phone would be worse than pasting. */
    val items: String = "",
    val isSaving: Boolean = false,
    val error: String? = null
)

data class GatewayListDetail(
    val list: GatewayList,
    val items: UiState<List<GatewayListItem>> = UiState.Loading
)

data class GatewayListsUiState(
    val lists: UiState<List<GatewayList>> = UiState.Loading,
    val isRefreshing: Boolean = false,
    val form: GatewayListFormState? = null,
    val detail: GatewayListDetail? = null,
    val deletingId: String? = null
)

/** Splits the pasted block into entries, dropping blanks and duplicates so a stray blank line
 *  doesn't become an empty list item. */
fun parseListItems(raw: String): List<String> =
    raw.split('\n', ',')
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()

fun validateGatewayListForm(form: GatewayListFormState): String? = when {
    form.name.isBlank() -> "List name is required"
    parseListItems(form.items).isEmpty() -> "Add at least one entry, one per line"
    else -> null
}

fun gatewayListSubtitle(list: GatewayList): String {
    val type = GatewayListType.entries.firstOrNull { it.apiValue == list.type }?.label ?: list.type
    val count = list.count ?: 0
    return "$type · $count ${if (count == 1) "entry" else "entries"}"
}

class GatewayListsViewModel(
    private val accountId: String,
    private val repository: GatewayRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(GatewayListsUiState())
    val uiState: StateFlow<GatewayListsUiState> = _uiState.asStateFlow()

    init {
        load(isRefresh = false)
    }

    fun refresh() = load(isRefresh = true)

    private fun load(isRefresh: Boolean) {
        _uiState.update { if (isRefresh) it.copy(isRefreshing = true) else it.copy(lists = UiState.Loading) }
        viewModelScope.launch {
            when (val result = repository.listLists(accountId)) {
                is ApiResult.Success -> _uiState.update { it.copy(lists = UiState.Data(result.data), isRefreshing = false) }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(lists = UiState.Error(ErrorClassifier.classify(result)), isRefreshing = false)
                }
            }
        }
    }

    fun openForm() = _uiState.update { it.copy(form = GatewayListFormState()) }

    fun closeForm() = _uiState.update { it.copy(form = null) }

    fun updateForm(transform: (GatewayListFormState) -> GatewayListFormState) =
        _uiState.update { state -> state.form?.let { state.copy(form = transform(it)) } ?: state }

    /** Opens a list and fetches its entries, which the list response doesn't carry. */
    fun openDetail(list: GatewayList) {
        _uiState.update { it.copy(detail = GatewayListDetail(list = list)) }
        viewModelScope.launch {
            when (val result = repository.listItems(accountId, list.id)) {
                is ApiResult.Success -> _uiState.update { state ->
                    val detail = state.detail
                    if (detail?.list?.id != list.id) state
                    else state.copy(detail = detail.copy(items = UiState.Data(result.data)))
                }
                is ApiResult.Failure -> _uiState.update { state ->
                    val detail = state.detail
                    if (detail?.list?.id != list.id) state
                    else state.copy(detail = detail.copy(items = UiState.Error(ErrorClassifier.classify(result))))
                }
            }
        }
    }

    fun closeDetail() = _uiState.update { it.copy(detail = null) }

    fun save() {
        val form = _uiState.value.form ?: return
        val validationError = validateGatewayListForm(form)
        if (validationError != null) {
            updateForm { it.copy(error = validationError) }
            return
        }
        updateForm { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            val list = GatewayListCreate(
                name = form.name.trim(),
                type = form.type.apiValue,
                description = form.description.trim().ifBlank { null },
                items = parseListItems(form.items).map { GatewayListItem(value = it) }
            )
            when (val result = repository.createList(accountId, list)) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(form = null) }
                    load(isRefresh = true)
                }
                is ApiResult.Failure -> updateForm { it.copy(isSaving = false, error = result.message) }
            }
        }
    }

    fun delete(list: GatewayList) {
        _uiState.update { it.copy(deletingId = list.id) }
        viewModelScope.launch {
            repository.deleteList(accountId, list.id)
            _uiState.update { it.copy(deletingId = null, detail = null) }
            load(isRefresh = true)
        }
    }
}

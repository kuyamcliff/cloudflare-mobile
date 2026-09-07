package dev.cfmobile.app.ui.zoneproducts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cfmobile.app.core.errors.ErrorClassifier
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.dto.CustomPage
import dev.cfmobile.app.data.repository.CustomPagesRepository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CustomPageFormState(
    val page: CustomPage,
    val url: String = "",
    val isSaving: Boolean = false,
    val error: String? = null
)

data class CustomPagesUiState(
    val pages: UiState<List<CustomPage>> = UiState.Loading,
    val isRefreshing: Boolean = false,
    val form: CustomPageFormState? = null,
    val revertingId: String? = null,
    val error: String? = null
)

/** Cloudflare's page identifiers are snake_case; this reads them out without inventing names
 *  for page types it doesn't know. */
fun customPageLabel(page: CustomPage): String {
    val description = page.description?.takeIf { it.isNotBlank() }
    if (description != null) return description
    return page.id.split('_').joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }
}

fun isCustomized(page: CustomPage): Boolean = page.state == CustomPagesRepository.STATE_CUSTOMIZED

fun customPageStatus(page: CustomPage): String =
    if (isCustomized(page)) page.url ?: "Customized" else "Cloudflare's default page"

/**
 * A custom page has to be HTML Cloudflare can fetch over HTTPS, and Cloudflare requires the
 * page to contain the tokens it substitutes at serve time - which this app can't check, so it
 * validates what it can and lets Cloudflare reject the rest.
 */
fun validateCustomPageForm(form: CustomPageFormState): String? = when {
    form.url.isBlank() -> "Page URL is required"
    !form.url.trim().startsWith("https://") -> "The URL must start with https:// - Cloudflare won't fetch a plain-HTTP page"
    else -> null
}

/** The placeholders Cloudflare requires the hosted HTML to contain for this page type. */
fun requiredTokensLabel(page: CustomPage): String? =
    page.requiredTokens?.takeIf { it.isNotEmpty() }?.joinToString(", ")

class CustomPagesViewModel(
    private val zoneId: String,
    private val repository: CustomPagesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CustomPagesUiState())
    val uiState: StateFlow<CustomPagesUiState> = _uiState.asStateFlow()

    init {
        load(isRefresh = false)
    }

    fun refresh() = load(isRefresh = true)

    private fun load(isRefresh: Boolean) {
        _uiState.update { if (isRefresh) it.copy(isRefreshing = true) else it.copy(pages = UiState.Loading) }
        viewModelScope.launch {
            when (val result = repository.listPages(zoneId)) {
                is ApiResult.Success -> _uiState.update { it.copy(pages = UiState.Data(result.data), isRefreshing = false) }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(pages = UiState.Error(ErrorClassifier.classify(result)), isRefreshing = false)
                }
            }
        }
    }

    fun openForm(page: CustomPage) =
        _uiState.update { it.copy(form = CustomPageFormState(page = page, url = page.url.orEmpty())) }

    fun closeForm() = _uiState.update { it.copy(form = null) }

    fun updateForm(transform: (CustomPageFormState) -> CustomPageFormState) =
        _uiState.update { state -> state.form?.let { state.copy(form = transform(it)) } ?: state }

    fun save() {
        val form = _uiState.value.form ?: return
        val validationError = validateCustomPageForm(form)
        if (validationError != null) {
            updateForm { it.copy(error = validationError) }
            return
        }
        updateForm { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.customize(zoneId, form.page.id, form.url.trim())) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(form = null) }
                    load(isRefresh = true)
                }
                is ApiResult.Failure -> updateForm { it.copy(isSaving = false, error = result.message) }
            }
        }
    }

    /** Puts the page back to Cloudflare's own, which is the safe direction and so isn't
     *  treated as a destructive delete. */
    fun revert(page: CustomPage) {
        _uiState.update { it.copy(revertingId = page.id, error = null) }
        viewModelScope.launch {
            when (val result = repository.revert(zoneId, page.id)) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(revertingId = null) }
                    load(isRefresh = true)
                }
                is ApiResult.Failure -> _uiState.update { it.copy(revertingId = null, error = result.message) }
            }
        }
    }

    fun dismissError() = _uiState.update { it.copy(error = null) }
}

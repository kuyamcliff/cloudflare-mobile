package dev.cfmobile.app.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cfmobile.app.core.errors.ErrorClassifier
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.dto.RumSite
import dev.cfmobile.app.data.repository.WebAnalyticsRepository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WebAnalyticsFormState(
    val host: String = "",
    /** Cloudflare injects the beacon itself for a proxied zone, so nothing has to be pasted
     *  into the site's HTML. */
    val autoInstall: Boolean = true,
    val isSaving: Boolean = false,
    val error: String? = null
)

data class WebAnalyticsUiState(
    val sites: UiState<List<RumSite>> = UiState.Loading,
    val isRefreshing: Boolean = false,
    val form: WebAnalyticsFormState? = null,
    val snippetSite: RumSite? = null,
    val deletingTag: String? = null
)

private val HOST_REGEX = Regex("^[a-zA-Z0-9]([a-zA-Z0-9-]*[a-zA-Z0-9])?(\\.[a-zA-Z0-9]([a-zA-Z0-9-]*[a-zA-Z0-9])?)+$")

fun validateWebAnalyticsForm(form: WebAnalyticsFormState): String? = when {
    form.host.isBlank() -> "Hostname is required"
    !form.host.trim().matches(HOST_REGEX) -> "Enter a hostname, e.g. example.com"
    else -> null
}

/** A site's own zone is the useful label; the site tag is the fallback identifier. */
fun rumSiteLabel(site: RumSite): String =
    site.ruleset?.zoneName?.takeIf { it.isNotBlank() } ?: site.siteTag

fun rumSiteSubtitle(site: RumSite): String = listOfNotNull(
    if (site.autoInstall == true) "Auto-installed" else "Snippet required",
    site.ruleset?.enabled?.let { if (it) "enabled" else "disabled" }
).joinToString(" · ")

class WebAnalyticsViewModel(
    private val accountId: String,
    private val repository: WebAnalyticsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(WebAnalyticsUiState())
    val uiState: StateFlow<WebAnalyticsUiState> = _uiState.asStateFlow()

    init {
        load(isRefresh = false)
    }

    fun refresh() = load(isRefresh = true)

    private fun load(isRefresh: Boolean) {
        _uiState.update { if (isRefresh) it.copy(isRefreshing = true) else it.copy(sites = UiState.Loading) }
        viewModelScope.launch {
            when (val result = repository.listSites(accountId)) {
                is ApiResult.Success -> _uiState.update { it.copy(sites = UiState.Data(result.data), isRefreshing = false) }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(sites = UiState.Error(ErrorClassifier.classify(result)), isRefreshing = false)
                }
            }
        }
    }

    fun openForm() = _uiState.update { it.copy(form = WebAnalyticsFormState()) }

    fun closeForm() = _uiState.update { it.copy(form = null) }

    fun updateForm(transform: (WebAnalyticsFormState) -> WebAnalyticsFormState) =
        _uiState.update { state -> state.form?.let { state.copy(form = transform(it)) } ?: state }

    fun showSnippet(site: RumSite) = _uiState.update { it.copy(snippetSite = site) }

    fun dismissSnippet() = _uiState.update { it.copy(snippetSite = null) }

    fun save() {
        val form = _uiState.value.form ?: return
        val validationError = validateWebAnalyticsForm(form)
        if (validationError != null) {
            updateForm { it.copy(error = validationError) }
            return
        }
        updateForm { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.createSite(accountId, form.host.trim(), form.autoInstall)) {
                is ApiResult.Success -> {
                    // A manually installed site is useless until its snippet is on the page, so
                    // the snippet is offered straight away rather than hidden behind a tap.
                    val created = result.data
                    _uiState.update {
                        it.copy(form = null, snippetSite = created.takeIf { site -> site.snippet != null })
                    }
                    load(isRefresh = true)
                }
                is ApiResult.Failure -> updateForm { it.copy(isSaving = false, error = result.message) }
            }
        }
    }

    fun delete(site: RumSite) {
        _uiState.update { it.copy(deletingTag = site.siteTag) }
        viewModelScope.launch {
            repository.deleteSite(accountId, site.siteTag)
            _uiState.update { it.copy(deletingTag = null, snippetSite = null) }
            load(isRefresh = true)
        }
    }
}

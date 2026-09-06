package dev.cfmobile.app.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cfmobile.app.core.errors.ErrorClassifier
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.dto.ApiToken
import dev.cfmobile.app.data.repository.ApiTokensRepository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ApiTokensUiState(
    val tokens: UiState<List<ApiToken>> = UiState.Loading,
    val isRefreshing: Boolean = false,
    val deletingId: String? = null,
    val error: String? = null
)

/** "Active · last used 2026-01-02", from whatever Cloudflare reported. */
fun apiTokenSummary(token: ApiToken): String {
    val status = token.status?.replaceFirstChar { it.uppercase() }
    return listOfNotNull(
        status,
        token.lastUsedOn?.let { "last used $it" } ?: "never used",
        token.expiresOn?.let { "expires $it" }
    ).joinToString(" · ")
}

class ApiTokensViewModel(
    private val repository: ApiTokensRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ApiTokensUiState())
    val uiState: StateFlow<ApiTokensUiState> = _uiState.asStateFlow()

    init {
        load(isRefresh = false)
    }

    fun refresh() = load(isRefresh = true)

    private fun load(isRefresh: Boolean) {
        _uiState.update { if (isRefresh) it.copy(isRefreshing = true) else it.copy(tokens = UiState.Loading) }
        viewModelScope.launch {
            when (val result = repository.listTokens()) {
                is ApiResult.Success -> _uiState.update { it.copy(tokens = UiState.Data(result.data), isRefreshing = false) }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(tokens = UiState.Error(ErrorClassifier.classify(result)), isRefreshing = false)
                }
            }
        }
    }

    /** Revoking a token is immediate and irreversible - and one of these tokens may well be
     *  the one this app is signed in with, which the screen warns about. */
    fun revoke(token: ApiToken) {
        _uiState.update { it.copy(deletingId = token.id, error = null) }
        viewModelScope.launch {
            when (val result = repository.deleteToken(token.id)) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(deletingId = null) }
                    load(isRefresh = true)
                }
                is ApiResult.Failure -> _uiState.update { it.copy(deletingId = null, error = result.message) }
            }
        }
    }

    fun dismissError() = _uiState.update { it.copy(error = null) }
}

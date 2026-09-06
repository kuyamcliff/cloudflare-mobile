package dev.cfmobile.app.ui.zerotrust

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cfmobile.app.core.errors.ErrorClassifier
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.dto.AccessIdentityProvider
import dev.cfmobile.app.data.remote.dto.AccessServiceToken
import dev.cfmobile.app.data.repository.AccessRepository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The two tabs of the identity screen: who can sign in, and which machines can. */
enum class IdentityTab(val label: String) {
    PROVIDERS("Login methods"),
    SERVICE_TOKENS("Service tokens")
}

data class NameFormState(
    val name: String = "",
    val isSaving: Boolean = false,
    val error: String? = null
)

/**
 * A service token's secret, shown exactly once. Cloudflare returns it only in the create
 * response and never again, so this is held in memory for the dialog and is not persisted.
 */
data class NewServiceToken(
    val name: String,
    val clientId: String,
    val clientSecret: String
)

data class AccessIdentityUiState(
    val tab: IdentityTab = IdentityTab.PROVIDERS,
    val providers: UiState<List<AccessIdentityProvider>> = UiState.Loading,
    val tokens: UiState<List<AccessServiceToken>> = UiState.Loading,
    val isRefreshing: Boolean = false,
    val form: NameFormState? = null,
    val newToken: NewServiceToken? = null,
    val deletingId: String? = null
)

/** Cloudflare's one-time PIN provider needs no configuration; every other type carries
 *  credentials this app doesn't ask for. */
fun isOneTimePin(provider: AccessIdentityProvider): Boolean =
    provider.type == AccessRepository.ONE_TIME_PIN_TYPE

/** Turns Cloudflare's provider type into something readable, falling back to the raw type for
 *  a provider this app doesn't know about rather than hiding it. */
fun identityProviderLabel(provider: AccessIdentityProvider): String = when (provider.type) {
    AccessRepository.ONE_TIME_PIN_TYPE -> "One-time PIN"
    "google" -> "Google"
    "google-apps" -> "Google Workspace"
    "azureAD" -> "Microsoft Entra ID"
    "okta" -> "Okta"
    "github" -> "GitHub"
    "saml" -> "SAML"
    "oidc" -> "OpenID Connect"
    "onelogin" -> "OneLogin"
    "centrify" -> "Centrify"
    "facebook" -> "Facebook"
    "linkedin" -> "LinkedIn"
    "yandex" -> "Yandex"
    "pingone" -> "PingOne"
    else -> provider.type.ifBlank { "Unknown provider" }
}

fun validateName(name: String, label: String): String? =
    if (name.isBlank()) "$label is required" else null

class AccessIdentityViewModel(
    private val accountId: String,
    private val repository: AccessRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AccessIdentityUiState())
    val uiState: StateFlow<AccessIdentityUiState> = _uiState.asStateFlow()

    init {
        load(isRefresh = false)
    }

    fun refresh() = load(isRefresh = true)

    fun selectTab(tab: IdentityTab) = _uiState.update { it.copy(tab = tab) }

    /** Both lists load in one coroutine, sequentially, so the tabs are never half-populated and
     *  the requests don't race each other. */
    private fun load(isRefresh: Boolean) {
        _uiState.update {
            if (isRefresh) it.copy(isRefreshing = true)
            else it.copy(providers = UiState.Loading, tokens = UiState.Loading)
        }
        viewModelScope.launch {
            when (val providers = repository.listIdentityProviders(accountId)) {
                is ApiResult.Success -> _uiState.update { it.copy(providers = UiState.Data(providers.data)) }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(providers = UiState.Error(ErrorClassifier.classify(providers)))
                }
            }
            when (val tokens = repository.listServiceTokens(accountId)) {
                is ApiResult.Success -> _uiState.update { it.copy(tokens = UiState.Data(tokens.data), isRefreshing = false) }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(tokens = UiState.Error(ErrorClassifier.classify(tokens)), isRefreshing = false)
                }
            }
        }
    }

    fun openForm() = _uiState.update { it.copy(form = NameFormState()) }

    fun closeForm() = _uiState.update { it.copy(form = null) }

    fun updateForm(transform: (NameFormState) -> NameFormState) =
        _uiState.update { state -> state.form?.let { state.copy(form = transform(it)) } ?: state }

    fun dismissNewToken() = _uiState.update { it.copy(newToken = null) }

    fun save() {
        val form = _uiState.value.form ?: return
        val tab = _uiState.value.tab
        val label = if (tab == IdentityTab.PROVIDERS) "Login method name" else "Token name"
        val validationError = validateName(form.name, label)
        if (validationError != null) {
            updateForm { it.copy(error = validationError) }
            return
        }
        updateForm { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            when (tab) {
                IdentityTab.PROVIDERS -> when (val result = repository.createOneTimePinProvider(accountId, form.name.trim())) {
                    is ApiResult.Success -> {
                        _uiState.update { it.copy(form = null) }
                        load(isRefresh = true)
                    }
                    is ApiResult.Failure -> updateForm { it.copy(isSaving = false, error = result.message) }
                }
                IdentityTab.SERVICE_TOKENS -> when (val result = repository.createServiceToken(accountId, form.name.trim())) {
                    is ApiResult.Success -> {
                        val token = result.data
                        _uiState.update {
                            it.copy(
                                form = null,
                                // Only surfaced when Cloudflare actually returned a secret;
                                // this is the only chance the user gets to copy it.
                                newToken = token.clientSecret?.let { secret ->
                                    NewServiceToken(
                                        name = token.name,
                                        clientId = token.clientId.orEmpty(),
                                        clientSecret = secret
                                    )
                                }
                            )
                        }
                        load(isRefresh = true)
                    }
                    is ApiResult.Failure -> updateForm { it.copy(isSaving = false, error = result.message) }
                }
            }
        }
    }

    fun deleteProvider(provider: AccessIdentityProvider) {
        _uiState.update { it.copy(deletingId = provider.id) }
        viewModelScope.launch {
            repository.deleteIdentityProvider(accountId, provider.id)
            _uiState.update { it.copy(deletingId = null) }
            load(isRefresh = true)
        }
    }

    fun deleteToken(token: AccessServiceToken) {
        _uiState.update { it.copy(deletingId = token.id) }
        viewModelScope.launch {
            repository.deleteServiceToken(accountId, token.id)
            _uiState.update { it.copy(deletingId = null) }
            load(isRefresh = true)
        }
    }
}

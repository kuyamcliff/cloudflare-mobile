package dev.cfmobile.app.ui.tokens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cfmobile.app.core.errors.ErrorClassifier
import dev.cfmobile.app.core.tokens.TokenPolicyBuilder
import dev.cfmobile.app.core.tokens.TokenRisk
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.dto.ApiToken
import dev.cfmobile.app.data.repository.ApiTokensRepository
import dev.cfmobile.app.data.repository.AuthRepository
import dev.cfmobile.app.data.repository.TokenOwner
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ScopeLine(val kind: String, val permissions: List<String>, val resources: List<String>, val deny: Boolean)

data class TokenDetailUiState(
    val token: UiState<ApiToken> = UiState.Loading,
    val scopes: List<ScopeLine> = emptyList(),
    val flags: List<TokenRisk.Flag> = emptyList(),
    val busy: Boolean = false,
    val message: String? = null,
    val rolledSecret: String? = null,
    val savedAsProfile: Boolean = false,
    val savingProfile: Boolean = false,
    val deleted: Boolean = false,
    val isThisDevicesToken: Boolean = false
)

class TokenDetailViewModel(
    private val owner: TokenOwner,
    private val tokenId: String,
    private val repository: ApiTokensRepository,
    private val authRepository: AuthRepository,
    private val activeTokenId: () -> String?,
    private val names: suspend () -> Map<String, String>
) : ViewModel() {
    private val _uiState = MutableStateFlow(TokenDetailUiState())
    val uiState: StateFlow<TokenDetailUiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            when (val r = repository.get(owner, tokenId)) {
                is ApiResult.Success -> {
                    val lookup = names()
                    _uiState.update {
                        it.copy(
                            token = UiState.Data(r.data),
                            scopes = describe(r.data, lookup),
                            flags = TokenRisk.flags(r.data),
                            isThisDevicesToken = activeTokenId() == r.data.id
                        )
                    }
                }
                is ApiResult.Failure -> _uiState.update { it.copy(token = UiState.Error(ErrorClassifier.classify(r))) }
            }
        }
    }

    fun update(name: String, active: Boolean, expiresOn: String?, allowedIps: String, deniedIps: String) {
        val token = (uiState.value.token as? UiState.Data)?.value ?: return
        val problems = (TokenPolicyBuilder.parseIps(allowedIps) + TokenPolicyBuilder.parseIps(deniedIps)).filterNot(TokenPolicyBuilder::isValidCidr)
        if (problems.isNotEmpty()) { _uiState.update { it.copy(message = "Not a valid IP or CIDR: ${problems.joinToString()}") }; return }
        if (!expiresOn.isNullOrBlank() && TokenPolicyBuilder.parseInstant(expiresOn) == null) {
            _uiState.update { it.copy(message = "Expiration must be an ISO date like 2027-01-31") }; return
        }
        _uiState.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            val body = TokenPolicyBuilder.buildUpdate(token, name, active, expiresOn?.takeIf { it.isNotBlank() }?.let { TokenPolicyBuilder.parseInstant(it).toString() }, allowedIps, deniedIps)
            when (val r = repository.update(owner, tokenId, body)) {
                is ApiResult.Success -> { _uiState.update { it.copy(busy = false, message = "Token updated") }; load() }
                is ApiResult.Failure -> _uiState.update { it.copy(busy = false, message = r.message) }
            }
        }
    }

    fun roll() {
        _uiState.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            when (val r = repository.roll(owner, tokenId)) {
                is ApiResult.Success -> {
                    val own = _uiState.value.isThisDevicesToken
                    if (own) authRepository.replaceActiveSecret(r.data)
                    _uiState.update {
                        it.copy(
                            busy = false,
                            rolledSecret = r.data,
                            savedAsProfile = own,
                            message = if (own) "This device now uses the new secret." else null
                        )
                    }
                }
                is ApiResult.Failure -> _uiState.update { it.copy(busy = false, message = r.message) }
            }
        }
    }

    fun revoke() {
        _uiState.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            when (val r = repository.delete(owner, tokenId)) {
                is ApiResult.Success -> _uiState.update { it.copy(busy = false, deleted = true) }
                is ApiResult.Failure -> _uiState.update { it.copy(busy = false, message = r.message) }
            }
        }
    }

    fun saveRolledAsProfile(label: String) {
        val secret = _uiState.value.rolledSecret ?: return
        val tokenName = (uiState.value.token as? UiState.Data)?.value?.name.orEmpty()
        _uiState.update { it.copy(savingProfile = true) }
        viewModelScope.launch {
            when (val r = authRepository.addToken(label.ifBlank { tokenName }, secret)) {
                is ApiResult.Success -> _uiState.update { it.copy(savingProfile = false, savedAsProfile = true) }
                is ApiResult.Failure -> _uiState.update { it.copy(savingProfile = false, message = r.message) }
            }
        }
    }

    /** Drops the secret from memory as soon as the user leaves the panel. */
    fun dismissSecret() = _uiState.update { it.copy(rolledSecret = null, savedAsProfile = false) }
    fun dismissMessage() = _uiState.update { it.copy(message = null) }

    override fun onCleared() {
        _uiState.update { it.copy(rolledSecret = null) }
    }

    companion object {
        private const val ACCOUNT = "com.cloudflare.api.account."
        private const val ZONE = "com.cloudflare.api.account.zone."

        /** Human-readable scope lines (spec 142, 146), resolving IDs to names where known. */
        fun describe(token: ApiToken, names: Map<String, String>): List<ScopeLine> =
            token.policies.orEmpty().map { p ->
                val resources = p.resources.flatMap { (key, value) ->
                    val label = when {
                        key == "${ZONE}*" -> "All zones"
                        key.startsWith(ZONE) -> "Zone " + (names[key.removePrefix(ZONE)] ?: key.removePrefix(ZONE))
                        key == "${ACCOUNT}*" -> "All accounts"
                        key.startsWith(ACCOUNT) -> "Account " + (names[key.removePrefix(ACCOUNT)] ?: key.removePrefix(ACCOUNT))
                        key.startsWith("com.cloudflare.api.user") -> "Your user"
                        else -> key
                    }
                    if (value is Map<*, *>) {
                        value.keys.map { k ->
                            val z = k.toString()
                            if (z == "${ZONE}*") "All zones in $label".replace("Account ", "account ")
                            else "Zone " + (names[z.removePrefix(ZONE)] ?: z.removePrefix(ZONE))
                        }
                    } else listOf(label)
                }
                val kind = when {
                    p.resources.keys.any { it.startsWith(ZONE) } || p.resources.values.any { it is Map<*, *> } -> "Zone"
                    p.resources.keys.any { it.startsWith("com.cloudflare.api.user") } -> "User"
                    else -> "Account"
                }
                ScopeLine(kind, p.permissionGroups.map { it.name ?: it.id }, resources, deny = p.effect.equals("deny", true))
            }
    }
}

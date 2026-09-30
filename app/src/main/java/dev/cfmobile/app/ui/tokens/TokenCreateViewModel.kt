package dev.cfmobile.app.ui.tokens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cfmobile.app.core.errors.ErrorClassifier
import dev.cfmobile.app.core.tokens.AccountSelection
import dev.cfmobile.app.core.tokens.ExpiryChoice
import dev.cfmobile.app.core.tokens.PermissionKind
import dev.cfmobile.app.core.tokens.TokenDraft
import dev.cfmobile.app.core.tokens.TokenPolicyBuilder
import dev.cfmobile.app.core.tokens.TokenTemplates
import dev.cfmobile.app.core.tokens.ZoneSelection
import dev.cfmobile.app.core.tokens.kind
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.dto.CfAccount
import dev.cfmobile.app.data.remote.dto.CfZone
import dev.cfmobile.app.data.remote.dto.PermissionGroup
import dev.cfmobile.app.data.repository.AccountsRepository
import dev.cfmobile.app.data.repository.ApiTokensRepository
import dev.cfmobile.app.data.repository.AuthRepository
import dev.cfmobile.app.data.repository.TokenOwner
import dev.cfmobile.app.data.repository.ZonesRepository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TokenCreateUiState(
    val catalog: UiState<List<PermissionGroup>> = UiState.Loading,
    val accounts: List<CfAccount> = emptyList(),
    val zones: List<CfZone> = emptyList(),
    val draft: TokenDraft = TokenDraft(),
    val problems: List<String> = emptyList(),
    val templateNote: String? = null,
    val reviewing: Boolean = false,
    val submitting: Boolean = false,
    val error: String? = null,
    val secret: String? = null,
    val createdName: String? = null,
    val savedAsProfile: Boolean = false,
    val savingProfile: Boolean = false
) {
    val kinds: Set<PermissionKind> get() = draft.selected.map { it.kind() }.toSet()
}

class TokenCreateViewModel(
    private val owner: TokenOwner,
    private val tokens: ApiTokensRepository,
    private val accountsRepository: AccountsRepository,
    private val zonesRepository: ZonesRepository,
    private val authRepository: AuthRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(TokenCreateUiState())
    val uiState: StateFlow<TokenCreateUiState> = _uiState.asStateFlow()
    private var userId: String? = null
    private val ownerAccountId = (owner as? TokenOwner.Account)?.accountId

    init { load() }

    fun load() {
        _uiState.update { it.copy(catalog = UiState.Loading) }
        viewModelScope.launch {
            val groups = async { tokens.permissionGroups(owner) }
            val accounts = async { accountsRepository.listAccounts() }
            val zones = async { zonesRepository.listZones(accountId = ownerAccountId) }
            val user = async { if (owner is TokenOwner.User) tokens.currentUser() else null }
            (user.await() as? ApiResult.Success)?.let { userId = it.data.id }
            val accountList = (accounts.await() as? ApiResult.Success)?.data.orEmpty()
            val zoneList = (zones.await() as? ApiResult.Success)?.data.orEmpty()
            when (val g = groups.await()) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(
                        catalog = UiState.Data(g.data.filter { pg -> ownerAccountId == null || pg.kind() != PermissionKind.USER }.sortedBy { pg -> pg.name.lowercase() }),
                        accounts = accountList,
                        zones = zoneList,
                        draft = if (ownerAccountId != null) it.draft.copy(zones = ZoneSelection.AllInAccount(ownerAccountId)) else it.draft
                    )
                }
                is ApiResult.Failure -> _uiState.update { it.copy(catalog = UiState.Error(ErrorClassifier.classify(g)), accounts = accountList, zones = zoneList) }
            }
        }
    }

    fun edit(transform: (TokenDraft) -> TokenDraft) = _uiState.update { it.copy(draft = transform(it.draft), problems = emptyList(), error = null) }

    fun toggle(group: PermissionGroup) = edit { d ->
        if (d.selected.any { it.id == group.id }) d.copy(selected = d.selected.filter { it.id != group.id }) else d.copy(selected = d.selected + group)
    }

    fun applyTemplate(template: TokenTemplates.Template) {
        val catalog = (uiState.value.catalog as? UiState.Data)?.value ?: return
        val (found, missing) = TokenTemplates.resolve(template, catalog, ownerAccountId != null)
        _uiState.update {
            it.copy(
                draft = it.draft.copy(name = it.draft.name.ifBlank { template.title }, selected = found.distinctBy { g -> g.id }),
                templateNote = if (missing.isEmpty()) null else "Not offered by Cloudflare for this token type: ${missing.joinToString()}"
            )
        }
    }

    fun review() {
        val problems = TokenPolicyBuilder.validate(uiState.value.draft, ownerAccountId != null)
        _uiState.update { it.copy(problems = problems, reviewing = problems.isEmpty()) }
    }

    fun backToEdit() = _uiState.update { it.copy(reviewing = false) }

    fun create() {
        val draft = uiState.value.draft
        if (draft.selected.any { it.kind() == PermissionKind.USER } && userId == null && ownerAccountId == null) {
            _uiState.update { it.copy(error = "User permissions need the token to read User Details. Remove them or use a token that can.") }
            return
        }
        val body = TokenPolicyBuilder.build(draft, ownerAccountId, userId)
        _uiState.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            when (val r = tokens.create(owner, body)) {
                is ApiResult.Success -> {
                    val value = r.data.value
                    _uiState.update {
                        it.copy(
                            submitting = false,
                            secret = value,
                            createdName = r.data.name,
                            error = if (value == null) "Token created, but Cloudflare did not return its secret." else null
                        )
                    }
                }
                is ApiResult.Failure -> _uiState.update { it.copy(submitting = false, error = r.message) }
            }
        }
    }

    fun saveAsProfile(label: String) {
        val secret = uiState.value.secret ?: return
        _uiState.update { it.copy(savingProfile = true) }
        viewModelScope.launch {
            when (val r = authRepository.addToken(label.ifBlank { uiState.value.createdName.orEmpty() }, secret)) {
                is ApiResult.Success -> _uiState.update { it.copy(savingProfile = false, savedAsProfile = true) }
                is ApiResult.Failure -> _uiState.update { it.copy(savingProfile = false, error = r.message) }
            }
        }
    }

    /** Human summary for the review step (spec 145). States scope, not guaranteed behavior. */
    fun summaryLines(): List<String> {
        val s = uiState.value
        val d = s.draft
        val zoneName = { id: String -> s.zones.firstOrNull { it.id == id }?.name ?: id }
        val accountName = { id: String -> s.accounts.firstOrNull { it.id == id }?.name ?: id }
        val zoneScope = when (val z = d.zones) {
            ZoneSelection.All -> if (ownerAccountId != null) "all zones in ${accountName(ownerAccountId)}" else "all zones"
            is ZoneSelection.AllInAccount -> "all zones in ${accountName(z.accountId)}"
            is ZoneSelection.Specific -> if (z.ids.size == 1) zoneName(z.ids.first()) else "${z.ids.size} zones"
        }
        val accountScope = when {
            ownerAccountId != null -> accountName(ownerAccountId)
            d.accounts is AccountSelection.Specific -> (d.accounts as AccountSelection.Specific).ids.joinToString { accountName(it) }
            else -> "all accounts"
        }
        return d.selected.map { g ->
            when (g.kind()) {
                PermissionKind.ZONE -> "${g.name}: $zoneScope"
                PermissionKind.USER -> "${g.name}: your user"
                else -> "${g.name}: $accountScope"
            }
        }
    }

    override fun onCleared() {
        _uiState.update { it.copy(secret = null) }
    }
}

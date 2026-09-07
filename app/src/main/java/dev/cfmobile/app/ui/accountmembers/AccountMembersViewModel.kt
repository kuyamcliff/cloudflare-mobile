package dev.cfmobile.app.ui.accountmembers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cfmobile.app.core.errors.ErrorClassifier
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.dto.AccountMember
import dev.cfmobile.app.data.remote.dto.AccountRole
import dev.cfmobile.app.data.repository.AccountMembersRepository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class InviteFormState(
    val email: String = "",
    val selectedRoleIds: Set<String> = emptySet(),
    val isSaving: Boolean = false,
    val error: String? = null
)

data class AccountMembersUiState(
    val members: UiState<List<AccountMember>> = UiState.Loading,
    val roles: List<AccountRole> = emptyList(),
    val form: InviteFormState? = null,
    val removingId: String? = null
)

class AccountMembersViewModel(
    private val accountId: String,
    private val repository: AccountMembersRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AccountMembersUiState())
    val uiState: StateFlow<AccountMembersUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    /** Roles are fetched alongside members - they're only used to populate the invite form's
     *  role picker (each member's own roles already come embedded in the member list), so a
     *  failure to load them just leaves that picker empty rather than failing the whole screen. */
    fun refresh() {
        _uiState.update { it.copy(members = UiState.Loading) }
        viewModelScope.launch {
            val roles = (repository.listRoles(accountId) as? ApiResult.Success)?.data ?: emptyList()
            when (val result = repository.listMembers(accountId)) {
                is ApiResult.Success -> _uiState.update { it.copy(members = UiState.Data(result.data), roles = roles) }
                is ApiResult.Failure -> _uiState.update { it.copy(members = UiState.Error(ErrorClassifier.classify(result))) }
            }
        }
    }

    fun openInviteForm() = _uiState.update { it.copy(form = InviteFormState()) }
    fun closeInviteForm() = _uiState.update { it.copy(form = null) }

    fun updateForm(transform: (InviteFormState) -> InviteFormState) =
        _uiState.update { state -> state.form?.let { state.copy(form = transform(it)) } ?: state }

    fun toggleRole(roleId: String) = updateForm {
        it.copy(selectedRoleIds = if (roleId in it.selectedRoleIds) it.selectedRoleIds - roleId else it.selectedRoleIds + roleId)
    }

    fun invite() {
        val form = _uiState.value.form ?: return
        val validationError = when {
            form.email.isBlank() -> "Email is required"
            form.selectedRoleIds.isEmpty() -> "Select at least one role"
            else -> null
        }
        if (validationError != null) {
            updateForm { it.copy(error = validationError) }
            return
        }
        updateForm { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            val result = repository.inviteMember(accountId, form.email.trim(), form.selectedRoleIds.toList())
            when (result) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(form = null) }
                    refresh()
                }
                is ApiResult.Failure -> updateForm { it.copy(isSaving = false, error = result.message) }
            }
        }
    }

    fun remove(member: AccountMember) {
        _uiState.update { it.copy(removingId = member.id) }
        viewModelScope.launch {
            repository.removeMember(accountId, member.id)
            _uiState.update { it.copy(removingId = null) }
            refresh()
        }
    }
}

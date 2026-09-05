package dev.cfmobile.app.ui.settings

import androidx.lifecycle.ViewModel
import dev.cfmobile.app.data.local.AccountSummary
import dev.cfmobile.app.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SettingsUiState(
    val accounts: List<AccountSummary> = emptyList(),
    val activeId: String? = null,
    val signedOut: Boolean = false
)

class SettingsViewModel(private val authRepository: AuthRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(loadState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private fun loadState() = SettingsUiState(
        accounts = authRepository.savedAccounts,
        activeId = authRepository.activeAccount?.id
    )

    fun refresh() {
        _uiState.value = loadState()
    }

    fun switchTo(id: String) {
        authRepository.switchTo(id)
        refresh()
    }

    fun remove(id: String) {
        authRepository.removeAccount(id)
        refresh()
        if (authRepository.activeAccount == null) {
            _uiState.value = _uiState.value.copy(signedOut = true)
        }
    }

    fun signOutAll() {
        authRepository.signOutAll()
        _uiState.value = SettingsUiState(signedOut = true)
    }
}

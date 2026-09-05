package dev.cfmobile.app.ui.settings

import androidx.lifecycle.ViewModel
import dev.cfmobile.app.data.local.SavedToken
import dev.cfmobile.app.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SettingsUiState(
    val tokens: List<SavedToken> = emptyList(),
    val activeId: String? = null,
    val signedOut: Boolean = false
)

class SettingsViewModel(private val authRepository: AuthRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(loadState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private fun loadState() = SettingsUiState(
        tokens = authRepository.savedTokens,
        activeId = authRepository.activeToken?.id
    )

    fun refresh() {
        _uiState.value = loadState()
    }

    fun switchTo(id: String) {
        authRepository.switchTo(id)
        refresh()
    }

    fun remove(id: String) {
        authRepository.removeToken(id)
        refresh()
        if (authRepository.activeToken == null) {
            _uiState.value = _uiState.value.copy(signedOut = true)
        }
    }

    fun signOutAll() {
        authRepository.signOutAll()
        _uiState.value = SettingsUiState(signedOut = true)
    }
}

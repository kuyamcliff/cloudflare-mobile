package dev.cfmobile.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cfmobile.app.core.security.AppSettings
import dev.cfmobile.app.core.security.AppSettingsSnapshot
import dev.cfmobile.app.data.local.AccountSummary
import dev.cfmobile.app.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val accounts: List<AccountSummary> = emptyList(),
    val activeId: String? = null,
    val signedOut: Boolean = false,
    val message: String? = null
)

/** Local data operations the settings screen can trigger. Each only touches this device. */
interface LocalDataActions {
    suspend fun forgetProfile(profileId: String)
    suspend fun clearCache()
    suspend fun clearHistory()
    suspend fun clearSavedRequests()
    suspend fun clearEverything()
}

class SettingsViewModel(
    private val authRepository: AuthRepository,
    private val appSettings: AppSettings,
    private val data: LocalDataActions
) : ViewModel() {

    private val _uiState = MutableStateFlow(loadState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    val settings: StateFlow<AppSettingsSnapshot> = appSettings.state

    private fun loadState() = SettingsUiState(accounts = authRepository.savedAccounts, activeId = authRepository.activeAccount?.id)

    fun refresh() { _uiState.value = loadState().copy(message = _uiState.value.message) }

    fun switchTo(id: String) { authRepository.switchTo(id); refresh() }

    fun rename(id: String, label: String) { authRepository.renameProfile(id, label); refresh() }

    /** Removes the token from this device only. Cloudflare is not contacted (spec 341). */
    fun remove(id: String) {
        viewModelScope.launch {
            data.forgetProfile(id)
            authRepository.removeAccount(id)
            refresh()
            if (authRepository.activeAccount == null) _uiState.update { it.copy(signedOut = true) }
        }
    }

    fun updateSettings(transform: (AppSettingsSnapshot) -> AppSettingsSnapshot) = appSettings.update(transform)

    fun clearCache() = run("Cached Cloudflare data cleared") { data.clearCache() }
    fun clearHistory() = run("Request history cleared") { data.clearHistory() }
    fun clearSaved() = run("Saved templates and queries cleared") { data.clearSavedRequests() }

    fun clearEverything() {
        viewModelScope.launch {
            data.clearEverything()
            authRepository.signOutAll()
            _uiState.value = SettingsUiState(signedOut = true)
        }
    }

    fun dismissMessage() = _uiState.update { it.copy(message = null) }

    private fun run(done: String, block: suspend () -> Unit) {
        viewModelScope.launch { block(); _uiState.update { it.copy(message = done) } }
    }
}

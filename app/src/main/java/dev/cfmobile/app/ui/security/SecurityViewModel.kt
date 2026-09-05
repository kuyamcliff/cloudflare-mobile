package dev.cfmobile.app.ui.security

import androidx.lifecycle.ViewModel
import dev.cfmobile.app.core.security.AppLockState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SecurityUiState(
    val appLockEnabled: Boolean,
    val lockTimeoutSeconds: Int,
    val screenshotProtectionEnabled: Boolean
)

/** Backs the Local Security Center (PRD §26). */
class SecurityViewModel(private val appLockState: AppLockState) : ViewModel() {

    private val _uiState = MutableStateFlow(loadState())
    val uiState: StateFlow<SecurityUiState> = _uiState.asStateFlow()

    private fun loadState() = SecurityUiState(
        appLockEnabled = appLockState.isAppLockEnabled(),
        lockTimeoutSeconds = appLockState.lockTimeoutSeconds(),
        screenshotProtectionEnabled = appLockState.isScreenshotProtectionEnabled()
    )

    fun setAppLockEnabled(enabled: Boolean) {
        appLockState.setAppLockEnabled(enabled)
        _uiState.value = loadState()
    }

    fun setLockTimeoutSeconds(seconds: Int) {
        appLockState.setLockTimeoutSeconds(seconds)
        _uiState.value = loadState()
    }

    fun setScreenshotProtectionEnabled(enabled: Boolean) {
        appLockState.setScreenshotProtectionEnabled(enabled)
        _uiState.value = loadState()
    }

    /** PRD §26.1 - invalidates the unlocked state without touching stored credentials. */
    fun lockNow() = appLockState.lockNow()
}

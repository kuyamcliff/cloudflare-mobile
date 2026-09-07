package dev.cfmobile.app.core.security

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Runtime lock state (PRD §48, §26.1). Pure logic, independent of Android's lifecycle APIs or
 * BiometricPrompt, so it's fully unit-testable - [dev.cfmobile.app.CfApplication] wires
 * [onAppBackgrounded]/[onAppForegrounded] to `ProcessLifecycleOwner`, and the lock screen
 * wires [unlock] to a successful [BiometricAuthenticator] result.
 */
class AppLockState(private val preferences: AppLockPreferences) {

    private val _isLocked = MutableStateFlow(preferences.isEnabled())
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    private var backgroundedAtMillis: Long? = null

    fun onAppBackgrounded(nowMillis: Long = System.currentTimeMillis()) {
        if (preferences.isEnabled()) backgroundedAtMillis = nowMillis
    }

    fun onAppForegrounded(nowMillis: Long = System.currentTimeMillis()) {
        if (!preferences.isEnabled()) return
        val backgroundedAt = backgroundedAtMillis ?: return
        val elapsedSeconds = (nowMillis - backgroundedAt) / 1000
        if (elapsedSeconds >= preferences.lockTimeoutSeconds()) {
            _isLocked.value = true
        }
    }

    fun unlock() {
        _isLocked.value = false
        backgroundedAtMillis = null
    }

    /** PRD §26.1 "Lock now" - invalidates the unlocked state without touching stored
     *  credentials. */
    fun lockNow() {
        _isLocked.value = true
    }

    fun setAppLockEnabled(enabled: Boolean) {
        preferences.setEnabled(enabled)
        if (!enabled) _isLocked.value = false
    }

    fun isAppLockEnabled(): Boolean = preferences.isEnabled()

    fun lockTimeoutSeconds(): Int = preferences.lockTimeoutSeconds()

    fun setLockTimeoutSeconds(seconds: Int) = preferences.setLockTimeoutSeconds(seconds)

    fun isScreenshotProtectionEnabled(): Boolean = preferences.isScreenshotProtectionEnabled()

    fun setScreenshotProtectionEnabled(enabled: Boolean) = preferences.setScreenshotProtectionEnabled(enabled)
}

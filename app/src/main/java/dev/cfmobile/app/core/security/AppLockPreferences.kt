package dev.cfmobile.app.core.security

import android.content.Context
import android.content.SharedPreferences

/** Available lock-timeout choices, in seconds. 0 means "lock immediately on background". */
val LOCK_TIMEOUT_OPTIONS_SECONDS = listOf(0, 30, 60, 300, 900)

/** Not secret data - just user preference about whether/when to require re-authentication.
 *  Plain prefs are the right tool here, unlike [dev.cfmobile.app.data.local.CredentialStore]. */
class AppLockPreferences(private val prefs: SharedPreferences) {

    fun isEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, false)

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun lockTimeoutSeconds(): Int = prefs.getInt(KEY_TIMEOUT_SECONDS, DEFAULT_TIMEOUT_SECONDS)

    fun setLockTimeoutSeconds(seconds: Int) {
        prefs.edit().putInt(KEY_TIMEOUT_SECONDS, seconds).apply()
    }

    /** PRD §26/§38/§47: block this app's content from screenshots and the recent-apps
     *  thumbnail. Lives here alongside app lock since both are local-security toggles a user
     *  sets once and rarely revisits. */
    fun isScreenshotProtectionEnabled(): Boolean = prefs.getBoolean(KEY_SCREENSHOT_PROTECTION, false)

    fun setScreenshotProtectionEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SCREENSHOT_PROTECTION, enabled).apply()
    }

    companion object {
        private const val PREFS_NAME = "cf_app_lock_prefs"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_TIMEOUT_SECONDS = "timeout_seconds"
        private const val KEY_SCREENSHOT_PROTECTION = "screenshot_protection"
        const val DEFAULT_TIMEOUT_SECONDS = 0

        fun create(context: Context): AppLockPreferences =
            AppLockPreferences(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE))
    }
}

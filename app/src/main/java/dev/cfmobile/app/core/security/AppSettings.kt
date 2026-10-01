package dev.cfmobile.app.core.security

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettingsSnapshot(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val clipboardClearSeconds: Int = 30,
    val historyRetentionDays: Int = 30,
    val transfersWifiOnly: Boolean = false,
    val transferConcurrency: Int = 2,
    val confirmMobileDataAboveMb: Int = 100
)

/** Non-secret app preferences (spec 105, 338). Plain SharedPreferences on purpose: none of
 *  this is sensitive, and it must be readable before the vault is unlocked. */
class AppSettings(private val prefs: SharedPreferences) {

    private val _state = MutableStateFlow(read())
    val state: StateFlow<AppSettingsSnapshot> = _state.asStateFlow()

    private fun read() = AppSettingsSnapshot(
        themeMode = runCatching { ThemeMode.valueOf(prefs.getString("theme", ThemeMode.SYSTEM.name)!!) }.getOrDefault(ThemeMode.SYSTEM),
        clipboardClearSeconds = prefs.getInt("clipboard_clear", 30),
        historyRetentionDays = prefs.getInt("history_days", 30),
        transfersWifiOnly = prefs.getBoolean("wifi_only", false),
        transferConcurrency = prefs.getInt("transfer_concurrency", 2),
        confirmMobileDataAboveMb = prefs.getInt("mobile_confirm_mb", 100)
    )

    fun update(transform: (AppSettingsSnapshot) -> AppSettingsSnapshot) {
        val next = transform(_state.value)
        prefs.edit()
            .putString("theme", next.themeMode.name)
            .putInt("clipboard_clear", next.clipboardClearSeconds.coerceIn(10, 600))
            .putInt("history_days", next.historyRetentionDays.coerceIn(1, 365))
            .putBoolean("wifi_only", next.transfersWifiOnly)
            .putInt("transfer_concurrency", next.transferConcurrency.coerceIn(1, 8))
            .putInt("mobile_confirm_mb", next.confirmMobileDataAboveMb.coerceAtLeast(0))
            .apply()
        _state.value = read()
    }

    companion object {
        val CLIPBOARD_OPTIONS = listOf(15, 30, 60, 120)
        val HISTORY_OPTIONS = listOf(1, 7, 30, 90)
        val CONCURRENCY_OPTIONS = listOf(1, 2, 4, 8)

        fun create(context: Context) = AppSettings(context.getSharedPreferences("cf_app_settings", Context.MODE_PRIVATE))
    }
}

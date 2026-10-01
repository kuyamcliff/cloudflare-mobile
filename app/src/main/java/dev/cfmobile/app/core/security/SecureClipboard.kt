package dev.cfmobile.app.core.security

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Copies values to the clipboard. Sensitive values (token secrets, R2 secret keys) are marked
 * with [ClipDescription.EXTRA_IS_SENSITIVE] so Android 13+ hides them from the clipboard
 * preview and keyboard suggestions, and are cleared after [clearAfterMillis] unless the user
 * has copied something else in the meantime (spec 190).
 */
class SecureClipboard(
    private val context: Context,
    private val scope: CoroutineScope,
    private val clearAfterMillis: () -> Long
) {
    private var clearJob: Job? = null

    fun copy(label: String, value: String, sensitive: Boolean) {
        val manager = context.getSystemService(ClipboardManager::class.java) ?: return
        val clip = ClipData.newPlainText(label, value)
        if (sensitive) {
            clip.description.extras = PersistableBundle().apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                } else {
                    putBoolean("android.content.extra.IS_SENSITIVE", true)
                }
            }
        }
        manager.setPrimaryClip(clip)
        if (sensitive) scheduleClear(manager, label)
    }

    private fun scheduleClear(manager: ClipboardManager, label: String) {
        clearJob?.cancel()
        clearJob = scope.launch {
            delay(clearAfterMillis())
            // Only clear our own clip: never wipe something the user copied afterwards.
            val current = manager.primaryClipDescription?.label?.toString()
            if (current == label) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) manager.clearPrimaryClip()
                else manager.setPrimaryClip(ClipData.newPlainText("", ""))
            }
        }
    }
}

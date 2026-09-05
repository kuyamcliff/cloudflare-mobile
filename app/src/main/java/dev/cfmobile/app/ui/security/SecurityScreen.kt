package dev.cfmobile.app.ui.security

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cfmobile.app.core.security.BiometricAvailability
import dev.cfmobile.app.core.security.LOCK_TIMEOUT_OPTIONS_SECONDS
import dev.cfmobile.app.ui.common.OptionRow
import dev.cfmobile.app.ui.common.ToggleRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityScreen(
    viewModel: SecurityViewModel,
    biometricAvailability: BiometricAvailability,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Security") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {
            Text(
                "Your Cloudflare API token stays on this device and is sent directly to Cloudflare - never to a server we run.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 4.dp)
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

            val biometricLabel = when (biometricAvailability) {
                BiometricAvailability.AVAILABLE -> "Available on this device"
                BiometricAvailability.NONE_ENROLLED -> "No screen lock or biometric enrolled"
                BiometricAvailability.NO_HARDWARE -> "This device has no biometric hardware"
                BiometricAvailability.HARDWARE_UNAVAILABLE -> "Biometric hardware is temporarily unavailable"
                BiometricAvailability.UNSUPPORTED -> "Not supported on this device"
            }
            ToggleRow(
                title = "App lock",
                subtitle = "Require your device screen lock or biometric to open the app. $biometricLabel.",
                checked = uiState.appLockEnabled,
                isSaving = false,
                onToggle = { enabled ->
                    if (biometricAvailability == BiometricAvailability.AVAILABLE || !enabled) {
                        viewModel.setAppLockEnabled(enabled)
                    }
                }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

            if (uiState.appLockEnabled) {
                OptionRow(
                    title = "Lock after",
                    currentValue = uiState.lockTimeoutSeconds.toString(),
                    isSaving = false,
                    options = LOCK_TIMEOUT_OPTIONS_SECONDS.map { it.toString() to timeoutLabel(it) },
                    onSelect = { viewModel.setLockTimeoutSeconds(it.toInt()) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            }

            ToggleRow(
                title = "Screenshot protection",
                subtitle = "Hide app content from screenshots and the recent-apps thumbnail. Takes effect next time you open the app.",
                checked = uiState.screenshotProtectionEnabled,
                isSaving = false,
                onToggle = viewModel::setScreenshotProtectionEnabled
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Backup", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Connected accounts are excluded from Android's automatic backup and device-transfer - they never leave this device except as requests to Cloudflare.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

            if (uiState.appLockEnabled) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Emergency", style = MaterialTheme.typography.bodyLarge)
                    Button(onClick = viewModel::lockNow) { Text("Lock now") }
                }
            }
        }
    }
}

private fun timeoutLabel(seconds: Int): String = when (seconds) {
    0 -> "Immediately"
    30 -> "30 seconds"
    60 -> "1 minute"
    300 -> "5 minutes"
    900 -> "15 minutes"
    else -> "$seconds seconds"
}

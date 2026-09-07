package dev.cfmobile.app.ui.security

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.cfmobile.app.core.security.BiometricAvailability

/** Shown whenever [dev.cfmobile.app.core.security.AppLockState.isLocked] is true. Prompts for
 *  biometric/device-credential authentication automatically on first appearance (PRD §112
 *  Scenario J), with a manual retry button in case the system prompt was dismissed. */
@Composable
fun LockScreen(
    availability: BiometricAvailability,
    error: String?,
    onUnlockClick: () -> Unit
) {
    LaunchedEffect(Unit) {
        if (availability == BiometricAvailability.AVAILABLE) onUnlockClick()
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Filled.Lock,
            contentDescription = null,
            modifier = Modifier.padding(bottom = 16.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text("CF Mobile is locked", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(
            when (availability) {
                BiometricAvailability.AVAILABLE -> "Verify it's you to continue."
                BiometricAvailability.NONE_ENROLLED -> "Set up a screen lock or biometric in your device settings to use app lock."
                else -> "This device can't verify your identity right now."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
        )
        if (error != null) {
            Text(
                error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }
        if (availability == BiometricAvailability.AVAILABLE) {
            Button(onClick = onUnlockClick) { Text("Unlock") }
        }
    }
}

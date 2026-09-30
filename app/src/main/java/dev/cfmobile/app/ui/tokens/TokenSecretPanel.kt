package dev.cfmobile.app.ui.tokens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.cfmobile.app.ui.components.SecureWindow
import dev.cfmobile.app.ui.components.copyToClipboard
import dev.cfmobile.app.ui.theme.StatusColors

/**
 * Shows a newly created or rolled token secret (spec 140, 141). The window is secured while
 * this is visible, copying marks the clip sensitive and schedules clearing, and once the user
 * leaves, the secret is gone: the view model drops it and Cloudflare never returns it again.
 */
@Composable
fun TokenSecretPanel(
    title: String,
    secret: String,
    savedAsProfile: Boolean,
    savingProfile: Boolean,
    onSaveAsProfile: ((label: String) -> Unit)?,
    onDone: () -> Unit
) {
    SecureWindow()
    val context = LocalContext.current
    var revealed by remember { mutableStateOf(false) }
    var label by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            "This secret is shown only now. Cloudflare will not show it again. Copy it or save it to this device before leaving.",
            style = MaterialTheme.typography.bodyMedium,
            color = StatusColors.warning
        )
        Text(
            if (revealed) secret else secret.take(4) + "•".repeat(24) + secret.takeLast(4),
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(12.dp)
        )
        TextButton(onClick = { revealed = !revealed }) { Text(if (revealed) "Hide" else "Reveal") }
        Button(onClick = { copyToClipboard(context, "Cloudflare API token", secret, sensitive = true) }, modifier = Modifier.fillMaxWidth()) {
            Text("Copy secret")
        }
        if (onSaveAsProfile != null) {
            if (savedAsProfile) {
                Text("Saved as a profile on this device.", color = StatusColors.success, style = MaterialTheme.typography.bodyMedium)
            } else {
                OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text("Profile name (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedButton(onClick = { onSaveAsProfile(label) }, enabled = !savingProfile, modifier = Modifier.fillMaxWidth()) {
                    Text(if (savingProfile) "Verifying" else "Save securely on this device")
                }
            }
        }
        TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Done") }
    }
}

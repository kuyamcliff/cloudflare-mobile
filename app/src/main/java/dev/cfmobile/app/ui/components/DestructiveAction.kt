package dev.cfmobile.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.cfmobile.app.core.capabilities.DestructiveRisk

/** Where a mutation lands, shown before it runs (spec 133, 134, 135). */
data class MutationContext(
    val profile: String?,
    val account: String? = null,
    val zone: String? = null,
    val target: String? = null
)

/**
 * The one confirmation dialog for every destructive action (spec 90). The title names the
 * resource, the body states the consequence, the target context is listed, and CRITICAL
 * actions require typing the resource name before the button enables.
 */
@Composable
fun DestructiveConfirmDialog(
    title: String,
    consequence: String,
    confirmLabel: String,
    risk: DestructiveRisk,
    context: MutationContext?,
    typedConfirmation: String? = null,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    var typed by remember { mutableStateOf("") }
    val needsTyping = risk == DestructiveRisk.CRITICAL && !typedConfirmation.isNullOrBlank()
    val enabled = !needsTyping || typed.trim() == typedConfirmation?.trim()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(consequence, style = MaterialTheme.typography.bodyMedium)
                context?.let { MutationContextBlock(it) }
                if (needsTyping) {
                    Text("Type ${typedConfirmation} to confirm.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = typed,
                        onValueChange = { typed = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = enabled,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) { Text(confirmLabel, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun MutationContextBlock(context: MutationContext) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        context.profile?.let { ContextLine("Profile", it) }
        context.account?.let { ContextLine("Account", it) }
        context.zone?.let { ContextLine("Zone", it) }
        context.target?.let { ContextLine("Target", it) }
    }
}

@Composable
private fun ContextLine(label: String, value: String) {
    Text(
        "$label: $value",
        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

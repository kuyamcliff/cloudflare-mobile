package dev.cfmobile.app.ui.turnstile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cfmobile.app.ui.common.CfListScreen
import dev.cfmobile.app.ui.common.CopyIconButton
import dev.cfmobile.app.ui.common.DeletableListRow
import dev.cfmobile.app.ui.common.FormActions
import dev.cfmobile.app.ui.common.OptionRow

@Composable
fun TurnstileScreen(viewModel: TurnstileViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    CfListScreen(
        title = "Turnstile",
        onBack = onBack,
        state = uiState.widgets,
        isRefreshing = uiState.isRefreshing,
        onRefresh = viewModel::refresh,
        emptyMessage = "No Turnstile widgets yet",
        key = { it.sitekey },
        onCreate = viewModel::openForm,
        createContentDescription = "Create widget",
        searchPlaceholder = "Search widgets",
        searchMatches = { widget, query ->
            widget.name.contains(query, ignoreCase = true) ||
                widget.domains.any { it.contains(query, ignoreCase = true) }
        },
        header = {
            uiState.error?.let { error ->
                Text(
                    error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(16.dp, 4.dp)
                )
            }
        }
    ) { widget ->
        var confirmRotate by remember(widget.sitekey) { mutableStateOf(false) }
        DeletableListRow(
            icon = Icons.Filled.VerifiedUser,
            title = widget.name,
            subtitle = widget.domains.joinToString(", ").ifBlank { null },
            detail = listOfNotNull(widget.mode, widget.sitekey).joinToString(" · "),
            isDeleting = uiState.deletingSitekey == widget.sitekey,
            deleteContentDescription = "Delete widget",
            confirmTitle = "Delete widget?",
            confirmText = "\"${widget.name}\" will be permanently deleted, and any page still embedding its sitekey will stop validating. This can't be undone.",
            onDelete = { viewModel.delete(widget) },
            // Only the public sitekey is ever shown or copied here. The secret key is never
            // fetched; the one time this app sees one is the value a rotation returns.
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (uiState.rotatingSitekey == widget.sitekey) {
                        CircularProgressIndicator(Modifier.padding(4.dp))
                    } else {
                        IconButton(onClick = { confirmRotate = true }) {
                            Icon(Icons.Filled.Autorenew, contentDescription = "Rotate secret for ${widget.name}")
                        }
                    }
                    CopyIconButton(value = widget.sitekey, label = "sitekey")
                }
            }
        )

        if (confirmRotate) {
            AlertDialog(
                onDismissRequest = { confirmRotate = false },
                title = { Text("Rotate this secret?") },
                text = {
                    Text(
                        "The current secret stops verifying immediately, so every server still using it starts rejecting challenges until it's updated. The new value is shown once, right after.",
                        style = MaterialTheme.typography.bodySmall
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        confirmRotate = false
                        viewModel.rotateSecret(widget)
                    }) { Text("Rotate") }
                },
                dismissButton = { TextButton(onClick = { confirmRotate = false }) { Text("Cancel") } }
            )
        }
    }

    uiState.form?.let { form ->
        CreateWidgetSheet(form, onDismiss = viewModel::closeForm, viewModel = viewModel)
    }
    uiState.rotatedSecret?.let { rotated ->
        RotatedSecretDialog(rotated, onDismiss = viewModel::dismissRotatedSecret)
    }
}

/** The one and only time a Turnstile secret is visible in this app. It is held for this dialog
 *  and never written to storage or logs. */
@Composable
private fun RotatedSecretDialog(rotated: RotatedSecret, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Copy the new secret now") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "\"${rotated.widgetName}\" has a new secret. Update every server that verifies this widget - the old value is already rejected.",
                    style = MaterialTheme.typography.bodySmall
                )
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Secret key",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            rotated.secret,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    CopyIconButton(value = rotated.secret, label = "secret key")
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateWidgetSheet(form: TurnstileFormState, onDismiss: () -> Unit, viewModel: TurnstileViewModel) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Create widget", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 10.dp))
            OutlinedTextField(
                value = form.name,
                onValueChange = { v -> viewModel.updateForm { it.copy(name = v) } },
                label = { Text("Widget name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = form.domains,
                onValueChange = { v -> viewModel.updateForm { it.copy(domains = v) } },
                label = { Text("Domains") },
                placeholder = { Text("example.com, app.example.com") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
            )
            OptionRow(
                title = "Mode",
                currentValue = form.mode.name,
                options = TurnstileMode.entries.map { it.name to it.label },
                isSaving = false,
                onSelect = { v -> viewModel.updateForm { it.copy(mode = TurnstileMode.valueOf(v)) } }
            )
            if (form.error != null) {
                Text(
                    form.error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            FormActions(
                isSaving = form.isSaving,
                onCancel = onDismiss,
                onSave = viewModel::save,
                modifier = Modifier.padding(top = 14.dp)
            )
        }
    }
}

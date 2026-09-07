package dev.cfmobile.app.ui.workers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cfmobile.app.ui.common.CfListScreen
import dev.cfmobile.app.ui.common.DeletableListRow
import dev.cfmobile.app.ui.common.FormActions

@Composable
fun WorkerSecretsScreen(scriptName: String, viewModel: WorkerSecretsViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    CfListScreen(
        title = "Secrets",
        subtitle = scriptName,
        onBack = onBack,
        state = uiState.secrets,
        isRefreshing = uiState.isRefreshing,
        onRefresh = viewModel::refresh,
        emptyMessage = "This Worker has no secrets",
        key = { it.name },
        onCreate = viewModel::openCreateForm,
        createContentDescription = "Add secret",
        searchPlaceholder = "Search secrets",
        searchMatches = { secret, query -> secret.name.contains(query, ignoreCase = true) },
        header = {
            Text(
                // Worth stating on the screen, not just in the registry: this is why there is
                // no "reveal" affordance anywhere here.
                "Cloudflare returns names only - a secret's value is never readable once set, by this app or anything else. Replacing one overwrites it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp, 8.dp)
            )
            uiState.error?.let { error ->
                Text(
                    error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(16.dp, 4.dp)
                )
            }
        }
    ) { secret ->
        DeletableListRow(
            icon = Icons.Filled.Key,
            title = secret.name,
            monospaceTitle = true,
            subtitle = secret.type ?: "secret_text",
            isDeleting = uiState.deletingName == secret.name,
            deleteContentDescription = "Delete secret",
            confirmTitle = "Delete this secret?",
            confirmText = "\"${secret.name}\" disappears from the Worker's environment on its next request. Its value can't be recovered.",
            onDelete = { viewModel.delete(secret) },
            onClick = { viewModel.openReplaceForm(secret) }
        )
    }

    uiState.form?.let { form ->
        SecretSheet(form, onDismiss = viewModel::closeForm, viewModel = viewModel)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SecretSheet(
    form: SecretFormState,
    onDismiss: () -> Unit,
    viewModel: WorkerSecretsViewModel
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                if (form.isReplacing) "Replace secret" else "Add secret",
                style = MaterialTheme.typography.titleMedium
            )
            OutlinedTextField(
                value = form.name,
                onValueChange = { v -> viewModel.updateForm { it.copy(name = v) } },
                label = { Text("Name") },
                placeholder = { Text("API_TOKEN") },
                singleLine = true,
                // The name identifies which secret is being replaced, so it's fixed then.
                enabled = !form.isReplacing,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = form.value,
                onValueChange = { v -> viewModel.updateForm { it.copy(value = v) } },
                label = { Text("Value") },
                singleLine = true,
                // Masked on screen; it is also never logged, since the HTTP logger is headers
                // only, and never stored anywhere by this app.
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                if (form.isReplacing) {
                    "This overwrites the existing value. There's no way to see what it was."
                } else {
                    "The Worker picks the secret up on its next request. It's sent once and never read back."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (form.error != null) {
                Text(form.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            FormActions(isSaving = form.isSaving, onCancel = onDismiss, onSave = viewModel::save)
        }
    }
}

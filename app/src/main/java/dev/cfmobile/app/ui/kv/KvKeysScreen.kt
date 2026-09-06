package dev.cfmobile.app.ui.kv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cfmobile.app.ui.common.CfListScreen
import dev.cfmobile.app.ui.common.DeletableListRow
import dev.cfmobile.app.ui.common.FormActions

@Composable
fun KvKeysScreen(namespaceLabel: String, viewModel: KvKeysViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    CfListScreen(
        title = "Keys",
        subtitle = namespaceLabel,
        onBack = onBack,
        state = uiState.keys,
        isRefreshing = uiState.isRefreshing,
        onRefresh = viewModel::refresh,
        emptyMessage = "This namespace has no keys yet",
        key = { it.name },
        onCreate = viewModel::openCreateForm,
        createContentDescription = "Add key",
        searchPlaceholder = "Search keys",
        searchMatches = { key, query -> key.name.contains(query, ignoreCase = true) }
    ) { key ->
        DeletableListRow(
            icon = Icons.Filled.VpnKey,
            title = key.name,
            monospaceTitle = true,
            subtitle = kvExpiryLabel(key),
            isDeleting = uiState.deletingKey == key.name,
            deleteContentDescription = "Delete key",
            confirmTitle = "Delete key?",
            confirmText = "\"${key.name}\" and its value will be permanently deleted. This can't be undone.",
            onDelete = { viewModel.delete(key) },
            onClick = { viewModel.openEditForm(key) }
        )
    }

    uiState.form?.let { form ->
        KvValueSheet(form, onDismiss = viewModel::closeForm, viewModel = viewModel)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KvValueSheet(form: KvValueFormState, onDismiss: () -> Unit, viewModel: KvKeysViewModel) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                if (form.isEditing) "Edit key" else "Add key",
                style = MaterialTheme.typography.titleMedium
            )
            OutlinedTextField(
                value = form.key,
                onValueChange = { v -> viewModel.updateForm { it.copy(key = v) } },
                label = { Text("Key") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            if (form.isLoadingValue) {
                CircularProgressIndicator(Modifier.padding(8.dp))
            } else {
                OutlinedTextField(
                    value = form.value,
                    onValueChange = { v -> viewModel.updateForm { it.copy(value = v) } },
                    label = { Text("Value") },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 280.dp)
                )
            }
            if (form.isEditing) {
                Text(
                    "Renaming the key writes the value under the new name and removes the old one.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (form.error != null) {
                Text(form.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            FormActions(
                isSaving = form.isSaving,
                onCancel = onDismiss,
                onSave = viewModel::save,
                saveLabel = "Save"
            )
        }
    }
}

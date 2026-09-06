package dev.cfmobile.app.ui.kv

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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cfmobile.app.ui.common.CfListScreen
import dev.cfmobile.app.ui.common.DeletableListRow
import dev.cfmobile.app.ui.common.FormActions

@Composable
fun KvScreen(viewModel: KvViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    CfListScreen(
        title = "Workers KV",
        onBack = onBack,
        state = uiState.namespaces,
        isRefreshing = uiState.isRefreshing,
        onRefresh = viewModel::refresh,
        emptyMessage = "No KV namespaces yet",
        key = { it.id },
        onCreate = viewModel::openForm,
        createContentDescription = "Create namespace",
        searchPlaceholder = "Search namespaces",
        searchMatches = { namespace, query -> namespace.title.contains(query, ignoreCase = true) }
    ) { namespace ->
        DeletableListRow(
            icon = Icons.Filled.Key,
            title = namespace.title,
            subtitle = namespace.id,
            isDeleting = uiState.deletingId == namespace.id,
            deleteContentDescription = "Delete namespace",
            confirmTitle = "Delete namespace?",
            confirmText = "\"${namespace.title}\" and every key stored in it will be permanently deleted. This can't be undone.",
            onDelete = { viewModel.delete(namespace) }
        )
    }

    uiState.form?.let { form ->
        CreateNamespaceSheet(form, onDismiss = viewModel::closeForm, viewModel = viewModel)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateNamespaceSheet(form: KvFormState, onDismiss: () -> Unit, viewModel: KvViewModel) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Create namespace", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = form.title,
                onValueChange = { v -> viewModel.updateForm { it.copy(title = v) } },
                label = { Text("Namespace title") },
                placeholder = { Text("my-namespace") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            if (form.error != null) {
                Text(form.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            FormActions(isSaving = form.isSaving, onCancel = onDismiss, onSave = viewModel::save)
        }
    }
}

package dev.cfmobile.app.ui.d1

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Storage
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
fun D1Screen(viewModel: D1ViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    CfListScreen(
        title = "D1 Databases",
        onBack = onBack,
        state = uiState.databases,
        isRefreshing = uiState.isRefreshing,
        onRefresh = viewModel::refresh,
        emptyMessage = "No D1 databases yet",
        key = { it.uuid },
        onCreate = viewModel::openForm,
        createContentDescription = "Create database",
        searchPlaceholder = "Search databases",
        searchMatches = { database, query -> database.name.contains(query, ignoreCase = true) }
    ) { database ->
        val details = listOfNotNull(
            database.numTables?.let { "$it table${if (it == 1) "" else "s"}" },
            database.createdAt?.let { "Created $it" }
        ).joinToString(" · ").ifBlank { null }

        DeletableListRow(
            icon = Icons.Filled.Storage,
            title = database.name,
            monospaceTitle = true,
            subtitle = details,
            isDeleting = uiState.deletingUuid == database.uuid,
            deleteContentDescription = "Delete database",
            confirmTitle = "Delete database?",
            confirmText = "\"${database.name}\" and all its data will be permanently deleted. This can't be undone.",
            onDelete = { viewModel.delete(database) }
        )
    }

    uiState.form?.let { form ->
        CreateDatabaseSheet(form, onDismiss = viewModel::closeForm, viewModel = viewModel)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateDatabaseSheet(form: D1FormState, onDismiss: () -> Unit, viewModel: D1ViewModel) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Create database", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = form.name,
                onValueChange = { v -> viewModel.updateForm { it.copy(name = v) } },
                label = { Text("Database name") },
                placeholder = { Text("my-database") },
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

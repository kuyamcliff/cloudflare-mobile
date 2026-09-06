package dev.cfmobile.app.ui.vectorize

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ScatterPlot
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cfmobile.app.ui.common.CfListScreen
import dev.cfmobile.app.ui.common.DeletableListRow
import dev.cfmobile.app.ui.common.FormActions
import dev.cfmobile.app.ui.common.OptionRow

@Composable
fun VectorizeScreen(viewModel: VectorizeViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    CfListScreen(
        title = "Vectorize",
        onBack = onBack,
        state = uiState.indexes,
        isRefreshing = uiState.isRefreshing,
        onRefresh = viewModel::refresh,
        emptyMessage = "No Vectorize indexes yet",
        key = { it.name },
        onCreate = viewModel::openForm,
        createContentDescription = "Create index",
        searchPlaceholder = "Search indexes",
        searchMatches = { index, query -> index.name.contains(query, ignoreCase = true) }
    ) { index ->
        DeletableListRow(
            icon = Icons.Filled.ScatterPlot,
            title = index.name,
            monospaceTitle = true,
            subtitle = index.config?.let { "${it.dimensions} dimensions · ${it.metric}" },
            detail = index.description,
            isDeleting = uiState.deletingName == index.name,
            deleteContentDescription = "Delete index",
            confirmTitle = "Delete index?",
            confirmText = "\"${index.name}\" and every vector stored in it will be permanently deleted. This can't be undone.",
            onDelete = { viewModel.delete(index) }
        )
    }

    uiState.form?.let { form ->
        CreateIndexSheet(form, onDismiss = viewModel::closeForm, viewModel = viewModel)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateIndexSheet(form: VectorizeFormState, onDismiss: () -> Unit, viewModel: VectorizeViewModel) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Create index", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 10.dp))
            Text(
                "Dimensions and metric are fixed once the index exists - they can't be changed later.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 10.dp)
            )
            OutlinedTextField(
                value = form.name,
                onValueChange = { v -> viewModel.updateForm { it.copy(name = v) } },
                label = { Text("Index name") },
                placeholder = { Text("my-index") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = form.dimensions,
                onValueChange = { v -> viewModel.updateForm { it.copy(dimensions = v) } },
                label = { Text("Dimensions") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
            )
            OptionRow(
                title = "Distance metric",
                currentValue = form.metric.name,
                options = VectorizeMetric.entries.map { it.name to it.label },
                isSaving = false,
                onSelect = { v -> viewModel.updateForm { it.copy(metric = VectorizeMetric.valueOf(v)) } }
            )
            OutlinedTextField(
                value = form.description,
                onValueChange = { v -> viewModel.updateForm { it.copy(description = v) } },
                label = { Text("Description (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
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

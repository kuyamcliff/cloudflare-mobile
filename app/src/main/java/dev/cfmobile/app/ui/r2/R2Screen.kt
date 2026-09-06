package dev.cfmobile.app.ui.r2

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory2
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
fun R2Screen(viewModel: R2ViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    CfListScreen(
        title = "R2 Storage",
        onBack = onBack,
        state = uiState.buckets,
        isRefreshing = uiState.isRefreshing,
        onRefresh = viewModel::refresh,
        emptyMessage = "No R2 buckets yet",
        key = { it.name },
        onCreate = viewModel::openForm,
        createContentDescription = "Create bucket",
        searchPlaceholder = "Search buckets",
        searchMatches = { bucket, query -> bucket.name.contains(query, ignoreCase = true) }
    ) { bucket ->
        DeletableListRow(
            icon = Icons.Filled.Inventory2,
            title = bucket.name,
            monospaceTitle = true,
            subtitle = bucket.creationDate?.let { "Created $it" },
            isDeleting = uiState.deletingName == bucket.name,
            deleteContentDescription = "Delete bucket",
            confirmTitle = "Delete bucket?",
            confirmText = "\"${bucket.name}\" must be empty to delete. This can't be undone.",
            onDelete = { viewModel.delete(bucket) }
        )
    }

    uiState.form?.let { form ->
        CreateBucketSheet(form, onDismiss = viewModel::closeForm, viewModel = viewModel)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateBucketSheet(form: R2FormState, onDismiss: () -> Unit, viewModel: R2ViewModel) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Create bucket", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = form.name,
                onValueChange = { v -> viewModel.updateForm { it.copy(name = v) } },
                label = { Text("Bucket name") },
                placeholder = { Text("my-bucket") },
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

package dev.cfmobile.app.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMissedOutgoing
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
import dev.cfmobile.app.ui.common.UiState

@Composable
fun BulkRedirectsScreen(viewModel: BulkRedirectsViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    CfListScreen(
        title = "Bulk Redirects",
        onBack = onBack,
        state = uiState.lists,
        isRefreshing = uiState.isRefreshing,
        onRefresh = viewModel::refresh,
        emptyMessage = "No bulk redirect lists on this account",
        key = { it.id },
        onCreate = viewModel::openForm,
        createContentDescription = "Create redirect list",
        searchPlaceholder = "Search lists",
        searchMatches = { list, query -> list.name.contains(query, ignoreCase = true) }
    ) { list ->
        DeletableListRow(
            icon = Icons.AutoMirrored.Filled.CallMissedOutgoing,
            title = list.name,
            monospaceTitle = true,
            subtitle = bulkRedirectSubtitle(list),
            detail = list.description,
            isDeleting = uiState.deletingId == list.id,
            deleteContentDescription = "Delete list",
            confirmTitle = "Delete redirect list?",
            confirmText = "\"${list.name}\" and every redirect in it will be permanently deleted. This can't be undone.",
            onDelete = { viewModel.delete(list) },
            onClick = { viewModel.openDetail(list) }
        )
    }

    uiState.form?.let { form ->
        CreateListSheet(form, onDismiss = viewModel::closeForm, viewModel = viewModel)
    }

    uiState.detail?.let { detail ->
        RedirectItemsSheet(detail, onDismiss = viewModel::closeDetail)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateListSheet(
    form: BulkRedirectFormState,
    onDismiss: () -> Unit,
    viewModel: BulkRedirectsViewModel
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Create redirect list", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = form.name,
                onValueChange = { v -> viewModel.updateForm { it.copy(name = v) } },
                label = { Text("List name") },
                placeholder = { Text("marketing_redirects") },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = form.description,
                onValueChange = { v -> viewModel.updateForm { it.copy(description = v) } },
                label = { Text("Description") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                "The list is created empty. Adding redirects to it is a bulk upload Cloudflare runs asynchronously, which isn't implemented here - use the dashboard for that.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (form.error != null) {
                Text(form.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            FormActions(isSaving = form.isSaving, onCancel = onDismiss, onSave = viewModel::save, saveLabel = "Create")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RedirectItemsSheet(detail: BulkRedirectDetail, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.padding(20.dp).heightIn(max = 480.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(detail.list.name, style = MaterialTheme.typography.titleMedium, fontFamily = FontFamily.Monospace)
            Text(
                bulkRedirectSubtitle(detail.list),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            when (val items = detail.items) {
                is UiState.Loading -> Box(Modifier.fillMaxWidth().padding(24.dp)) {
                    CircularProgressIndicator(Modifier.padding(4.dp))
                }
                is UiState.Error -> Text(
                    items.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
                is UiState.Data -> if (items.value.isEmpty()) {
                    Text(
                        "This list has no redirects yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(items.value, key = { it.id }) { item ->
                            Text(
                                redirectItemSummary(item),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }
}

package dev.cfmobile.app.ui.zerotrust

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
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
import dev.cfmobile.app.ui.rules.RuleDropdown

@Composable
fun GatewayListsScreen(viewModel: GatewayListsViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    CfListScreen(
        title = "Gateway Lists",
        onBack = onBack,
        state = uiState.lists,
        isRefreshing = uiState.isRefreshing,
        onRefresh = viewModel::refresh,
        emptyMessage = "No Zero Trust lists yet",
        key = { it.id },
        onCreate = viewModel::openForm,
        createContentDescription = "Create list",
        searchPlaceholder = "Search lists",
        searchMatches = { list, query -> list.name.contains(query, ignoreCase = true) }
    ) { list ->
        DeletableListRow(
            icon = Icons.AutoMirrored.Filled.ListAlt,
            title = list.name,
            subtitle = gatewayListSubtitle(list),
            detail = list.description,
            isDeleting = uiState.deletingId == list.id,
            deleteContentDescription = "Delete list",
            confirmTitle = "Delete list?",
            confirmText = "Any Gateway policy matching against \"${list.name}\" will stop matching. This can't be undone.",
            onDelete = { viewModel.delete(list) },
            onClick = { viewModel.openDetail(list) }
        )
    }

    uiState.form?.let { form ->
        CreateListSheet(form, onDismiss = viewModel::closeForm, viewModel = viewModel)
    }

    uiState.detail?.let { detail ->
        ListItemsSheet(detail, onDismiss = viewModel::closeDetail)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateListSheet(
    form: GatewayListFormState,
    onDismiss: () -> Unit,
    viewModel: GatewayListsViewModel
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Create list", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = form.name,
                onValueChange = { v -> viewModel.updateForm { it.copy(name = v) } },
                label = { Text("List name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = form.description,
                onValueChange = { v -> viewModel.updateForm { it.copy(description = v) } },
                label = { Text("Description") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            RuleDropdown(
                label = "Type",
                options = GatewayListType.entries.map { it to it.label },
                selected = form.type,
                onSelect = { v -> viewModel.updateForm { it.copy(type = v) } }
            )
            OutlinedTextField(
                value = form.items,
                onValueChange = { v -> viewModel.updateForm { it.copy(items = v) } },
                label = { Text("Entries") },
                placeholder = { Text(form.type.placeholder) },
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp, max = 220.dp)
            )
            Text(
                "One entry per line. Blank lines and duplicates are dropped.",
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
private fun ListItemsSheet(detail: GatewayListDetail, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.padding(20.dp).heightIn(max = 480.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(detail.list.name, style = MaterialTheme.typography.titleMedium)
            Text(
                gatewayListSubtitle(detail.list),
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
                        "This list has no entries",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    // Read-only: editing entries one at a time is a bulk operation better done
                    // by replacing the list, which isn't implemented.
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        items(items.value) { item ->
                            Text(
                                item.value,
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

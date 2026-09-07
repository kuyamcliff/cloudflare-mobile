package dev.cfmobile.app.ui.zoneproducts

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import dev.cfmobile.app.ui.rules.RuleCommonFields
import dev.cfmobile.app.ui.rules.RuleDropdown
import dev.cfmobile.app.ui.rules.RuleSheetBody

@Composable
fun CloudConnectorScreen(zoneName: String, viewModel: CloudConnectorViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    CfListScreen(
        title = "Cloud Connector",
        subtitle = zoneName,
        onBack = onBack,
        state = uiState.rules,
        isRefreshing = uiState.isRefreshing,
        onRefresh = viewModel::refresh,
        emptyMessage = "No Cloud Connector rules on this zone",
        key = { it.id ?: it.expression },
        onCreate = viewModel::openCreateForm,
        createContentDescription = "Add rule",
        searchPlaceholder = "Search rules",
        searchMatches = { rule, query ->
            rule.description.orEmpty().contains(query, ignoreCase = true) ||
                rule.parameters?.host.orEmpty().contains(query, ignoreCase = true)
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
    ) { rule ->
        DeletableListRow(
            icon = Icons.Filled.CloudSync,
            title = rule.description?.takeIf { it.isNotBlank() } ?: cloudConnectorSummary(rule),
            subtitle = if (rule.description.isNullOrBlank()) null else cloudConnectorSummary(rule),
            detail = rule.expression,
            isDeleting = uiState.deletingId == rule.id,
            deleteContentDescription = "Delete rule",
            confirmTitle = "Delete rule?",
            confirmText = "Matching requests will go to the origin again instead of object storage.",
            onDelete = { viewModel.delete(rule) },
            onClick = { viewModel.openEditForm(rule) },
            trailing = {
                Switch(
                    checked = rule.enabled,
                    enabled = uiState.deletingId != rule.id,
                    onCheckedChange = { viewModel.setEnabled(rule, it) }
                )
            }
        )
    }

    uiState.form?.let { form ->
        CloudConnectorSheet(form, onDismiss = viewModel::closeForm, viewModel = viewModel)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CloudConnectorSheet(
    form: CloudConnectorFormState,
    onDismiss: () -> Unit,
    viewModel: CloudConnectorViewModel
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        RuleSheetBody {
            Text(
                if (form.editingId != null) "Edit rule" else "Add rule",
                style = MaterialTheme.typography.titleMedium
            )
            RuleCommonFields(
                expression = form.expression,
                onExpressionChange = { v -> viewModel.updateForm { it.copy(expression = v) } },
                description = form.description,
                onDescriptionChange = { v -> viewModel.updateForm { it.copy(description = v) } },
                enabled = form.enabled,
                onEnabledChange = { v -> viewModel.updateForm { it.copy(enabled = v) } }
            )
            RuleDropdown(
                label = "Provider",
                options = CloudProvider.entries.map { it to it.label },
                selected = form.provider,
                onSelect = { v -> viewModel.updateForm { it.copy(provider = v) } }
            )
            OutlinedTextField(
                value = form.host,
                onValueChange = { v -> viewModel.updateForm { it.copy(host = v) } },
                label = { Text("Bucket hostname") },
                placeholder = { Text(form.provider.hostPlaceholder) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                "The bucket has to allow public reads, or Cloudflare will forward requests it can't fulfil.",
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

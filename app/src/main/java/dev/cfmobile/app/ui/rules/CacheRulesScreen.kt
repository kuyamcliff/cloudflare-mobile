package dev.cfmobile.app.ui.rules

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cfmobile.app.ui.common.FormActions

@Composable
fun CacheRulesScreen(zoneName: String, viewModel: CacheRulesViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    PhaseRulesScreen(
        title = "Cache Rules",
        zoneName = zoneName,
        emptyMessage = "No cache rules on this zone",
        createContentDescription = "Add cache rule",
        state = uiState,
        summary = ::cacheSummary,
        onBack = onBack,
        onRefresh = viewModel::refresh,
        onCreate = viewModel::openCreateForm,
        onEdit = viewModel::openEditForm,
        onSetEnabled = viewModel::setEnabled,
        onDelete = viewModel::delete
    )

    uiState.form?.let { form ->
        CacheRuleSheet(form, onDismiss = viewModel::closeForm, viewModel = viewModel)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CacheRuleSheet(
    form: CacheRuleForm,
    onDismiss: () -> Unit,
    viewModel: CacheRulesViewModel
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        RuleSheetBody {
            Text(
                if (form.editingId != null) "Edit cache rule" else "Add cache rule",
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
            LabelledSwitch("Cache eligible content", form.cache) { v ->
                viewModel.updateForm { it.copy(cache = v) }
            }
            // TTLs only exist for content Cloudflare is actually caching.
            if (form.cache) {
                RuleDropdown(
                    label = "Edge TTL",
                    options = TTL_MODES,
                    selected = form.edgeTtlMode,
                    onSelect = { v -> viewModel.updateForm { it.copy(edgeTtlMode = v) } }
                )
                if (form.edgeTtlMode == TTL_OVERRIDE_ORIGIN) {
                    SecondsField("Edge TTL seconds", form.edgeTtlSeconds) { v ->
                        viewModel.updateForm { it.copy(edgeTtlSeconds = v) }
                    }
                }
                RuleDropdown(
                    label = "Browser TTL",
                    options = TTL_MODES,
                    selected = form.browserTtlMode,
                    onSelect = { v -> viewModel.updateForm { it.copy(browserTtlMode = v) } }
                )
                if (form.browserTtlMode == TTL_OVERRIDE_ORIGIN) {
                    SecondsField("Browser TTL seconds", form.browserTtlSeconds) { v ->
                        viewModel.updateForm { it.copy(browserTtlSeconds = v) }
                    }
                }
            } else {
                Text(
                    "Matching requests bypass the cache and always go to the origin.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (form.error != null) {
                Text(form.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            FormActions(isSaving = form.isSaving, onCancel = onDismiss, onSave = viewModel::save)
        }
    }
}

@Composable
private fun SecondsField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text("3600") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth()
    )
}

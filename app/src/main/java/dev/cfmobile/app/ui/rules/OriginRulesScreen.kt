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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cfmobile.app.ui.common.FormActions

@Composable
fun OriginRulesScreen(zoneName: String, viewModel: OriginRulesViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    PhaseRulesScreen(
        title = "Origin Rules",
        zoneName = zoneName,
        emptyMessage = "No origin rules on this zone",
        createContentDescription = "Add origin rule",
        state = uiState,
        summary = ::originSummary,
        onBack = onBack,
        onRefresh = viewModel::refresh,
        onCreate = viewModel::openCreateForm,
        onEdit = viewModel::openEditForm,
        onSetEnabled = viewModel::setEnabled,
        onDelete = viewModel::delete
    )

    uiState.form?.let { form ->
        OriginRuleSheet(form, onDismiss = viewModel::closeForm, viewModel = viewModel)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OriginRuleSheet(
    form: OriginRuleForm,
    onDismiss: () -> Unit,
    viewModel: OriginRulesViewModel
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        RuleSheetBody {
            Text(
                if (form.editingId != null) "Edit origin rule" else "Add origin rule",
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
            MonospaceField("Origin host", form.originHost, "origin.example.com") { v ->
                viewModel.updateForm { it.copy(originHost = v) }
            }
            OutlinedTextField(
                value = form.originPort,
                onValueChange = { v -> viewModel.updateForm { it.copy(originPort = v) } },
                label = { Text("Origin port") },
                placeholder = { Text("8443") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            MonospaceField("Host header", form.hostHeader, "www.example.com") { v ->
                viewModel.updateForm { it.copy(hostHeader = v) }
            }
            MonospaceField("SNI", form.sni, "www.example.com") { v ->
                viewModel.updateForm { it.copy(sni = v) }
            }
            Text(
                "Leave a field empty to keep whatever Cloudflare would have used. At least one override is required.",
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

@Composable
private fun MonospaceField(label: String, value: String, placeholder: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
        modifier = Modifier.fillMaxWidth()
    )
}

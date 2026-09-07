package dev.cfmobile.app.ui.rules

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cfmobile.app.ui.common.FormActions

@Composable
fun ConfigRulesScreen(zoneName: String, viewModel: ConfigRulesViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    PhaseRulesScreen(
        title = "Config Rules",
        zoneName = zoneName,
        emptyMessage = "No config rules on this zone",
        createContentDescription = "Add config rule",
        state = uiState,
        summary = ::configSummary,
        onBack = onBack,
        onRefresh = viewModel::refresh,
        onCreate = viewModel::openCreateForm,
        onEdit = viewModel::openEditForm,
        onSetEnabled = viewModel::setEnabled,
        onDelete = viewModel::delete
    )

    uiState.form?.let { form ->
        ConfigRuleSheet(form, onDismiss = viewModel::closeForm, viewModel = viewModel)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfigRuleSheet(
    form: ConfigRuleForm,
    onDismiss: () -> Unit,
    viewModel: ConfigRulesViewModel
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        RuleSheetBody {
            Text(
                if (form.editingId != null) "Edit config rule" else "Add config rule",
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
                label = "Setting",
                options = ConfigSetting.entries.map { it to it.label },
                selected = form.setting,
                onSelect = { v -> viewModel.updateForm { it.copy(setting = v) } }
            )
            Text(
                form.setting.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LabelledSwitch("Turn it on for matching requests", form.settingEnabled) { v ->
                viewModel.updateForm { it.copy(settingEnabled = v) }
            }
            Text(
                "A config rule overrides one zone setting for the traffic its expression matches, leaving the zone-wide value alone everywhere else.",
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

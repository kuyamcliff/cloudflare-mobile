package dev.cfmobile.app.ui.rules

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cfmobile.app.ui.common.FormActions

@Composable
fun RedirectRulesScreen(zoneName: String, viewModel: RedirectRulesViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    PhaseRulesScreen(
        title = "Redirect Rules",
        zoneName = zoneName,
        emptyMessage = "No redirect rules on this zone",
        createContentDescription = "Add redirect rule",
        state = uiState,
        summary = ::redirectSummary,
        onBack = onBack,
        onRefresh = viewModel::refresh,
        onCreate = viewModel::openCreateForm,
        onEdit = viewModel::openEditForm,
        onSetEnabled = viewModel::setEnabled,
        onDelete = viewModel::delete
    )

    uiState.form?.let { form ->
        RedirectRuleSheet(form, onDismiss = viewModel::closeForm, viewModel = viewModel)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RedirectRuleSheet(
    form: RedirectRuleForm,
    onDismiss: () -> Unit,
    viewModel: RedirectRulesViewModel
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        RuleSheetBody {
            Text(
                if (form.editingId != null) "Edit redirect rule" else "Add redirect rule",
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
            OutlinedTextField(
                value = form.target,
                onValueChange = { v -> viewModel.updateForm { it.copy(target = v) } },
                label = { Text(if (form.targetIsExpression) "Target expression" else "Target URL") },
                placeholder = { Text(if (form.targetIsExpression) "concat(\"https://example.com\", http.request.uri.path)" else "https://example.com/new") },
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.fillMaxWidth()
            )
            LabelledSwitch(
                "Target is an expression",
                form.targetIsExpression
            ) { v -> viewModel.updateForm { it.copy(targetIsExpression = v) } }
            RuleDropdown(
                label = "Status code",
                options = REDIRECT_STATUS_CODES.map { it to redirectStatusLabel(it) },
                selected = form.statusCode,
                onSelect = { v -> viewModel.updateForm { it.copy(statusCode = v) } }
            )
            LabelledSwitch(
                "Preserve query string",
                form.preserveQueryString
            ) { v -> viewModel.updateForm { it.copy(preserveQueryString = v) } }
            if (form.error != null) {
                Text(form.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            FormActions(isSaving = form.isSaving, onCancel = onDismiss, onSave = viewModel::save)
        }
    }
}

/** Spells out what each redirect code means, since 301 vs 302 is a decision with consequences. */
fun redirectStatusLabel(code: Int): String = when (code) {
    301 -> "301 Moved Permanently"
    302 -> "302 Found (temporary)"
    303 -> "303 See Other"
    307 -> "307 Temporary Redirect"
    308 -> "308 Permanent Redirect"
    else -> code.toString()
}

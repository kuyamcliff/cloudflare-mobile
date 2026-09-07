package dev.cfmobile.app.ui.magicfirewall

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material3.CircularProgressIndicator
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
import dev.cfmobile.app.ui.rules.LabelledSwitch
import dev.cfmobile.app.ui.rules.RuleDropdown
import dev.cfmobile.app.ui.rules.RuleSheetBody

@Composable
fun MagicFirewallScreen(viewModel: MagicFirewallViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    CfListScreen(
        title = "Magic Firewall",
        onBack = onBack,
        state = uiState.rules,
        isRefreshing = uiState.isRefreshing,
        onRefresh = viewModel::refresh,
        emptyMessage = "No Magic Firewall rules yet",
        key = { it.id },
        onCreate = viewModel::openForm,
        createContentDescription = "Add rule",
        header = {
            uiState.error?.let { error ->
                Text(
                    error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(16.dp, 4.dp)
                )
            }
            Text(
                // Worth being explicit: this filter runs on packets, before anything HTTP.
                "A packet filter in front of Magic Transit traffic. Rules are evaluated in order and match on IP headers, not on requests.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp, 8.dp)
            )
        }
    ) { rule ->
        DeletableListRow(
            icon = Icons.Filled.FilterAlt,
            title = rule.description?.ifBlank { null } ?: rule.expression,
            subtitle = ruleSummary(rule),
            detail = rule.expression,
            isDeleting = uiState.deletingId == rule.id,
            deleteContentDescription = "Delete rule",
            confirmTitle = "Delete this rule?",
            confirmText = "Packets it was matching fall through to the rules below it, which can mean they start being accepted. Disabling it instead keeps the rule.",
            onDelete = { viewModel.delete(rule) },
            onClick = { viewModel.editRule(rule) },
            trailing = {
                if (uiState.busyId == rule.id) {
                    CircularProgressIndicator(Modifier.padding(4.dp))
                } else {
                    Switch(
                        checked = rule.enabled,
                        onCheckedChange = { viewModel.setEnabled(rule, it) }
                    )
                }
            }
        )
    }

    uiState.form?.let { form ->
        RuleSheet(form, onDismiss = viewModel::closeForm, viewModel = viewModel)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RuleSheet(
    form: MagicFirewallFormState,
    onDismiss: () -> Unit,
    viewModel: MagicFirewallViewModel
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        RuleSheetBody {
            Text(
                if (form.editingId == null) "Add rule" else "Edit rule",
                style = MaterialTheme.typography.titleMedium
            )
            OutlinedTextField(
                value = form.description,
                onValueChange = { v -> viewModel.updateForm { it.copy(description = v) } },
                label = { Text("Description") },
                placeholder = { Text("Drop inbound SSH") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = form.expression,
                onValueChange = { v -> viewModel.updateForm { it.copy(expression = v) } },
                label = { Text("Expression") },
                placeholder = { Text("tcp.dstport == 22") },
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp, max = 160.dp)
            )
            RuleDropdown(
                label = "Action",
                options = MagicFirewallAction.entries.map { it to it.label },
                selected = form.action,
                onSelect = { v -> viewModel.updateForm { it.copy(action = v) } }
            )
            Text(
                form.action.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LabelledSwitch(
                label = "Enabled",
                checked = form.enabled,
                onCheckedChange = { v -> viewModel.updateForm { it.copy(enabled = v) } }
            )
            Text(
                "Expressions use Cloudflare's packet fields - ip.src, tcp.dstport, udp.len and the rest. A new rule is appended after the existing ones.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (form.error != null) {
                Text(form.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            FormActions(
                isSaving = form.isSaving,
                onCancel = onDismiss,
                onSave = viewModel::save,
                saveLabel = if (form.editingId == null) "Add" else "Save"
            )
        }
    }
}

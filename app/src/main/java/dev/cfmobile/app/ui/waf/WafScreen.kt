package dev.cfmobile.app.ui.waf

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cfmobile.app.data.remote.dto.RulesetRule
import dev.cfmobile.app.ui.common.EmptyState
import dev.cfmobile.app.ui.common.StateContent
import dev.cfmobile.app.ui.common.ZoneScopedTitle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WafScreen(viewModel: WafViewModel, zoneName: String, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { ZoneScopedTitle("WAF Custom Rules", zoneName) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = viewModel::openAddForm) {
                Icon(Icons.Filled.Add, contentDescription = "Add rule")
            }
        }
    ) { padding ->
        StateContent(state = uiState.rules, onRetry = viewModel::refresh) { rules ->
            if (rules.isEmpty()) {
                EmptyState("No custom rules yet", Modifier.padding(padding))
            } else {
                LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(bottom = 96.dp)) {
                    items(rules, key = { it.id }) { rule ->
                        WafRuleRow(
                            rule = rule,
                            zoneName = zoneName,
                            isDeleting = uiState.deletingId == rule.id,
                            onClick = { viewModel.openEditForm(rule) },
                            onToggleEnabled = { viewModel.toggleEnabled(rule) },
                            onDelete = { viewModel.delete(rule) }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    }
                }
            }
        }
    }

    uiState.form?.let { form ->
        WafRuleFormSheet(form, onDismiss = viewModel::closeForm, onFieldChange = viewModel::updateForm, onSave = viewModel::save)
    }
}

@Composable
private fun WafRuleRow(
    rule: RulesetRule,
    zoneName: String,
    isDeleting: Boolean,
    onClick: () -> Unit,
    onToggleEnabled: () -> Unit,
    onDelete: () -> Unit
) {
    var confirmDelete by remember { mutableStateOf(false) }

    Row(
        Modifier.fillMaxWidth().padding(16.dp, 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(Modifier.weight(1f).clickable(onClick = onClick)) {
            Text(
                rule.description?.ifBlank { rule.action } ?: rule.action,
                style = MaterialTheme.typography.bodyLarge,
                color = if (rule.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                rule.expression,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )
        }
        Switch(checked = rule.enabled, onCheckedChange = { onToggleEnabled() })
        if (isDeleting) {
            CircularProgressIndicator(Modifier.padding(4.dp))
        } else {
            IconButton(onClick = { confirmDelete = true }) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete custom rule?") },
            text = { Text("This ${rule.action} rule will stop applying to $zoneName immediately.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WafRuleFormSheet(
    form: WafRuleForm,
    onDismiss: () -> Unit,
    onFieldChange: ((WafRuleForm) -> WafRuleForm) -> Unit,
    onSave: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(if (form.editingId != null) "Edit custom rule" else "Add custom rule", style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = form.expression,
                onValueChange = { v -> onFieldChange { it.copy(expression = v) } },
                label = { Text("Expression") },
                placeholder = { Text("(http.request.uri.path contains \"/admin\")") },
                modifier = Modifier.fillMaxWidth()
            )

            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    value = form.action,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Action") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    WAF_RULE_ACTIONS.forEach { action ->
                        DropdownMenuItem(text = { Text(action) }, onClick = { onFieldChange { it.copy(action = action) }; expanded = false })
                    }
                }
            }

            OutlinedTextField(
                value = form.description,
                onValueChange = { v -> onFieldChange { it.copy(description = v) } },
                label = { Text("Description (optional)") },
                modifier = Modifier.fillMaxWidth()
            )

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Switch(checked = form.enabled, onCheckedChange = { v -> onFieldChange { it.copy(enabled = v) } })
                Text("Enabled")
            }

            if (form.error != null) {
                Text(form.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                TextButton(onClick = onSave, enabled = !form.isSaving) {
                    if (form.isSaving) CircularProgressIndicator(Modifier.padding(end = 6.dp))
                    Text("Save")
                }
            }
        }
    }
}

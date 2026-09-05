package dev.cfmobile.app.ui.pagerules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import dev.cfmobile.app.data.remote.dto.PageRule
import dev.cfmobile.app.ui.common.EmptyState
import dev.cfmobile.app.ui.common.StateContent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageRulesScreen(viewModel: PageRulesViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Page Rules") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = viewModel::openForm) { Icon(Icons.Filled.Add, contentDescription = "Add page rule") }
        }
    ) { padding ->
        StateContent(state = uiState.rules, onRetry = viewModel::refresh) { rules ->
            if (rules.isEmpty()) {
                EmptyState("No page rules yet", Modifier.padding(padding))
            } else {
                LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(bottom = 96.dp)) {
                    items(rules, key = { it.id }) { rule ->
                        PageRuleRow(rule, onToggle = { viewModel.toggleActive(rule) }, onDelete = { viewModel.delete(rule) })
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    }
                }
            }
        }
    }

    uiState.form?.let { form ->
        PageRuleFormSheet(form, onDismiss = viewModel::closeForm, onFieldChange = viewModel::updateForm, onSave = viewModel::save)
    }
}

@Composable
private fun PageRuleRow(rule: PageRule, onToggle: () -> Unit, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(16.dp, 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(rule.targets.firstOrNull()?.constraint?.value ?: "", style = MaterialTheme.typography.bodyLarge, fontFamily = FontFamily.Monospace, maxLines = 1)
            Text(
                rule.actions.joinToString(", ") { it.id.replace("_", " ") },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = rule.status == "active", onCheckedChange = { onToggle() })
        IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PageRuleFormSheet(
    form: PageRuleForm,
    onDismiss: () -> Unit,
    onFieldChange: ((PageRuleForm) -> PageRuleForm) -> Unit,
    onSave: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Add page rule", style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = form.urlPattern,
                onValueChange = { v -> onFieldChange { it.copy(urlPattern = v) } },
                label = { Text("URL pattern") },
                placeholder = { Text("example.com/admin/*") },
                modifier = Modifier.fillMaxWidth()
            )

            var actionExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded = actionExpanded, onExpandedChange = { actionExpanded = it }) {
                OutlinedTextField(
                    value = form.actionKind.label,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Setting") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = actionExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(expanded = actionExpanded, onDismissRequest = { actionExpanded = false }) {
                    PageRuleActionKind.entries.forEach { kind ->
                        DropdownMenuItem(
                            text = { Text(kind.label) },
                            onClick = { onFieldChange { it.copy(actionKind = kind, actionValue = "") }; actionExpanded = false }
                        )
                    }
                }
            }

            if (form.actionKind.takesStringValue) {
                var valueExpanded by remember { mutableStateOf(false) }
                val current = form.actionValue.ifBlank { form.actionKind.options.first() }
                ExposedDropdownMenuBox(expanded = valueExpanded, onExpandedChange = { valueExpanded = it }) {
                    OutlinedTextField(
                        value = current,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Value") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = valueExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = valueExpanded, onDismissRequest = { valueExpanded = false }) {
                        form.actionKind.options.forEach { option ->
                            DropdownMenuItem(text = { Text(option) }, onClick = { onFieldChange { it.copy(actionValue = option) }; valueExpanded = false })
                        }
                    }
                }
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

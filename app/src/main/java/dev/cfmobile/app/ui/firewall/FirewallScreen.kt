package dev.cfmobile.app.ui.firewall

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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cfmobile.app.data.remote.dto.AccessRule
import dev.cfmobile.app.data.remote.dto.FirewallRule
import dev.cfmobile.app.ui.common.EmptyState
import dev.cfmobile.app.ui.common.StateContent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FirewallScreen(viewModel: FirewallViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Firewall") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { if (tab == 0) viewModel.openRuleForm() else viewModel.openAccessRuleForm() }) {
                Icon(Icons.Filled.Add, contentDescription = "Add")
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding)) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Firewall Rules") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("IP Access Rules") })
            }
            if (tab == 0) {
                StateContent(state = uiState.rules, onRetry = viewModel::refresh) { rules ->
                    if (rules.isEmpty()) {
                        EmptyState("No firewall rules yet")
                    } else {
                        LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                            items(rules, key = { it.id }) { rule ->
                                FirewallRuleRow(rule, onDelete = { viewModel.deleteRule(rule) })
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                            }
                        }
                    }
                }
            } else {
                StateContent(state = uiState.accessRules, onRetry = viewModel::refresh) { rules ->
                    if (rules.isEmpty()) {
                        EmptyState("No IP access rules yet")
                    } else {
                        LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                            items(rules, key = { it.id }) { rule ->
                                AccessRuleRow(rule, onDelete = { viewModel.deleteAccessRule(rule) })
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                            }
                        }
                    }
                }
            }
        }
    }

    uiState.ruleForm?.let { form ->
        FirewallRuleFormSheet(form, onDismiss = viewModel::closeRuleForm, onFieldChange = viewModel::updateRuleForm, onSave = viewModel::saveRule)
    }
    uiState.accessRuleForm?.let { form ->
        AccessRuleFormSheet(form, onDismiss = viewModel::closeAccessRuleForm, onFieldChange = viewModel::updateAccessRuleForm, onSave = viewModel::saveAccessRule)
    }
}

@Composable
private fun FirewallRuleRow(rule: FirewallRule, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(16.dp, 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Column(Modifier.weight(1f)) {
            Text(rule.description?.ifBlank { rule.action } ?: rule.action, style = MaterialTheme.typography.bodyLarge)
            Text(rule.filter?.expression ?: "", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
        IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun AccessRuleRow(rule: AccessRule, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(16.dp, 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Column(Modifier.weight(1f)) {
            Text(rule.configuration.value, style = MaterialTheme.typography.bodyLarge, fontFamily = FontFamily.Monospace)
            Text(rule.mode.replace("_", " ").replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FirewallRuleFormSheet(
    form: FirewallRuleForm,
    onDismiss: () -> Unit,
    onFieldChange: ((FirewallRuleForm) -> FirewallRuleForm) -> Unit,
    onSave: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Add firewall rule", style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = form.expression,
                onValueChange = { v -> onFieldChange { it.copy(expression = v) } },
                label = { Text("Expression") },
                placeholder = { Text("ip.src eq 203.0.113.5") },
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
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    listOf("block", "challenge", "js_challenge", "managed_challenge", "allow", "log").forEach { action ->
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccessRuleFormSheet(
    form: AccessRuleForm,
    onDismiss: () -> Unit,
    onFieldChange: ((AccessRuleForm) -> AccessRuleForm) -> Unit,
    onSave: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Add IP access rule", style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = form.ip,
                onValueChange = { v -> onFieldChange { it.copy(ip = v) } },
                label = { Text("IP address or CIDR range") },
                placeholder = { Text("203.0.113.5 or 203.0.113.0/24") },
                modifier = Modifier.fillMaxWidth()
            )

            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    value = form.mode,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Mode") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    listOf("block", "challenge", "js_challenge", "managed_challenge", "whitelist").forEach { mode ->
                        DropdownMenuItem(text = { Text(mode) }, onClick = { onFieldChange { it.copy(mode = mode) }; expanded = false })
                    }
                }
            }

            OutlinedTextField(
                value = form.notes,
                onValueChange = { v -> onFieldChange { it.copy(notes = v) } },
                label = { Text("Notes (optional)") },
                modifier = Modifier.fillMaxWidth()
            )

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

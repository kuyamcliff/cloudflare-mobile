package dev.cfmobile.app.ui.ratelimit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cfmobile.app.data.remote.dto.RulesetRule
import dev.cfmobile.app.ui.common.EmptyState
import dev.cfmobile.app.ui.common.StateContent
import dev.cfmobile.app.ui.common.ZoneScopedTitle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RateLimitScreen(viewModel: RateLimitViewModel, zoneName: String, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { ZoneScopedTitle("Rate Limiting", zoneName) },
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
                EmptyState("No rate limiting rules yet", Modifier.padding(padding))
            } else {
                LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(bottom = 96.dp)) {
                    items(rules, key = { it.id }) { rule ->
                        RateLimitRuleRow(
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
        RateLimitFormSheet(form, onDismiss = viewModel::closeForm, onFieldChange = viewModel::updateForm, onSave = viewModel::save)
    }
}

@Composable
private fun RateLimitRuleRow(
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
            rule.ratelimit?.let {
                Text(
                    "${it.requestsPerPeriod} requests / ${periodLabel(it.period)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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
            title = { Text("Delete rate limiting rule?") },
            text = { Text("This rule will stop applying to $zoneName immediately.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
}

private fun periodLabel(seconds: Int): String = when (seconds) {
    10 -> "10s"
    60 -> "1min"
    600 -> "10min"
    3600 -> "1hr"
    86400 -> "24hr"
    else -> "${seconds}s"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RateLimitFormSheet(
    form: RateLimitRuleForm,
    onDismiss: () -> Unit,
    onFieldChange: ((RateLimitRuleForm) -> RateLimitRuleForm) -> Unit,
    onSave: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(if (form.editingId != null) "Edit rate limiting rule" else "Add rate limiting rule", style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = form.expression,
                onValueChange = { v -> onFieldChange { it.copy(expression = v) } },
                label = { Text("Expression") },
                placeholder = { Text("(http.request.uri.path eq \"/login\")") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = form.requestsPerPeriod,
                onValueChange = { v -> onFieldChange { it.copy(requestsPerPeriod = v.filter(Char::isDigit)) } },
                label = { Text("Requests per period") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            var periodExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded = periodExpanded, onExpandedChange = { periodExpanded = it }) {
                OutlinedTextField(
                    value = periodLabel(form.period),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Period") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = periodExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(expanded = periodExpanded, onDismissRequest = { periodExpanded = false }) {
                    RATE_LIMIT_PERIODS.forEach { seconds ->
                        DropdownMenuItem(
                            text = { Text(periodLabel(seconds)) },
                            onClick = { onFieldChange { it.copy(period = seconds) }; periodExpanded = false }
                        )
                    }
                }
            }

            var actionExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded = actionExpanded, onExpandedChange = { actionExpanded = it }) {
                OutlinedTextField(
                    value = form.action,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Action when exceeded") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = actionExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(expanded = actionExpanded, onDismissRequest = { actionExpanded = false }) {
                    RATE_LIMIT_ACTIONS.forEach { action ->
                        DropdownMenuItem(text = { Text(action) }, onClick = { onFieldChange { it.copy(action = action) }; actionExpanded = false })
                    }
                }
            }

            OutlinedTextField(
                value = form.mitigationTimeout,
                onValueChange = { v -> onFieldChange { it.copy(mitigationTimeout = v.filter(Char::isDigit)) } },
                label = { Text("Mitigation timeout (seconds, optional)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

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

package dev.cfmobile.app.ui.transformrules

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
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
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
fun TransformRulesScreen(viewModel: TransformRulesViewModel, zoneName: String, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { ZoneScopedTitle("Transform Rules", zoneName) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = viewModel::openAddForm) {
                Icon(Icons.Filled.Add, contentDescription = "Add rule")
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding)) {
            PrimaryTabRow(selectedTabIndex = uiState.selectedKind.ordinal) {
                TransformRuleKind.entries.forEach { kind ->
                    Tab(
                        selected = uiState.selectedKind == kind,
                        onClick = { viewModel.selectKind(kind) },
                        text = { Text(kind.label) }
                    )
                }
            }
            StateContent(state = uiState.activeState.rules, onRetry = viewModel::refresh) { rules ->
                if (rules.isEmpty()) {
                    EmptyState("No ${uiState.selectedKind.label.lowercase()} rules yet")
                } else {
                    LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                        items(rules, key = { it.id }) { rule ->
                            TransformRuleRow(
                                rule = rule,
                                kind = uiState.selectedKind,
                                zoneName = zoneName,
                                isDeleting = uiState.activeState.deletingId == rule.id,
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
    }

    uiState.form?.let { form ->
        TransformRuleFormSheet(form, onDismiss = viewModel::closeForm, onFieldChange = viewModel::updateForm, onSave = viewModel::save)
    }
}

@Composable
private fun TransformRuleRow(
    rule: RulesetRule,
    kind: TransformRuleKind,
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
                rule.description?.ifBlank { summaryFor(rule, kind) } ?: summaryFor(rule, kind),
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
            title = { Text("Delete rule?") },
            text = { Text("This rule will stop applying to $zoneName immediately.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
}

private fun summaryFor(rule: RulesetRule, kind: TransformRuleKind): String {
    val ap = rule.actionParameters
    return when (kind) {
        TransformRuleKind.URL_REWRITE -> {
            val path = ap?.uri?.path?.let { it.value ?: it.expression }
            val query = ap?.uri?.query?.let { it.value ?: it.expression }
            listOfNotNull(path?.let { "path: $it" }, query?.let { "query: $it" }).joinToString(", ").ifBlank { "URL rewrite" }
        }
        TransformRuleKind.REQUEST_HEADERS, TransformRuleKind.RESPONSE_HEADERS -> {
            val entry = ap?.headers?.entries?.firstOrNull()
            entry?.let { "${it.value.operation} ${it.key}" } ?: "Header rule"
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransformRuleFormSheet(
    form: TransformRuleForm,
    onDismiss: () -> Unit,
    onFieldChange: ((TransformRuleForm) -> TransformRuleForm) -> Unit,
    onSave: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                if (form.editingId != null) "Edit ${form.kind.label.lowercase()} rule" else "Add ${form.kind.label.lowercase()} rule",
                style = MaterialTheme.typography.titleMedium
            )

            OutlinedTextField(
                value = form.expression,
                onValueChange = { v -> onFieldChange { it.copy(expression = v) } },
                label = { Text("Expression") },
                placeholder = { Text("(http.request.uri.path contains \"/old\")") },
                modifier = Modifier.fillMaxWidth()
            )

            when (form.kind) {
                TransformRuleKind.URL_REWRITE -> {
                    RewriteField("Path", form.pathValue, form.pathIsExpression,
                        onValueChange = { v -> onFieldChange { it.copy(pathValue = v) } },
                        onExpressionToggle = { v -> onFieldChange { it.copy(pathIsExpression = v) } }
                    )
                    RewriteField("Query", form.queryValue, form.queryIsExpression,
                        onValueChange = { v -> onFieldChange { it.copy(queryValue = v) } },
                        onExpressionToggle = { v -> onFieldChange { it.copy(queryIsExpression = v) } }
                    )
                }
                TransformRuleKind.REQUEST_HEADERS, TransformRuleKind.RESPONSE_HEADERS -> {
                    OutlinedTextField(
                        value = form.headerName,
                        onValueChange = { v -> onFieldChange { it.copy(headerName = v) } },
                        label = { Text("Header name") },
                        placeholder = { Text("X-Custom-Header") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    var operationExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(expanded = operationExpanded, onExpandedChange = { operationExpanded = it }) {
                        OutlinedTextField(
                            value = form.headerOperation,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Operation") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = operationExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        )
                        ExposedDropdownMenu(expanded = operationExpanded, onDismissRequest = { operationExpanded = false }) {
                            HEADER_OPERATIONS.forEach { op ->
                                DropdownMenuItem(text = { Text(op) }, onClick = { onFieldChange { it.copy(headerOperation = op) }; operationExpanded = false })
                            }
                        }
                    }

                    if (form.headerOperation == "set") {
                        RewriteField("Value", form.headerValue, form.headerIsExpression,
                            onValueChange = { v -> onFieldChange { it.copy(headerValue = v) } },
                            onExpressionToggle = { v -> onFieldChange { it.copy(headerIsExpression = v) } }
                        )
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

/** A value field paired with a "Use expression" toggle - the same static-vs-dynamic choice
 *  appears for URI path/query and for header values, so this is shared rather than repeated
 *  three times. */
@Composable
private fun RewriteField(
    label: String,
    value: String,
    isExpression: Boolean,
    onValueChange: (String) -> Unit,
    onExpressionToggle: (Boolean) -> Unit
) {
    Column {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(if (isExpression) "$label expression" else label) },
            placeholder = { Text(if (isExpression) "concat(\"/new\", http.request.uri.path)" else "/new-path") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Switch(checked = isExpression, onCheckedChange = onExpressionToggle)
            Text("Use expression instead of a static value", style = MaterialTheme.typography.bodySmall)
        }
    }
}

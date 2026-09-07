package dev.cfmobile.app.ui.rules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.cfmobile.app.data.remote.dto.RulesetRule
import dev.cfmobile.app.ui.common.CfListScreen
import dev.cfmobile.app.ui.common.DeletableListRow

/**
 * The list half of a rules-engine screen. Every family shows the same thing - what the rule
 * does, the expression that selects traffic, an enable switch, and a delete - and differs only
 * in the summary line and the editor sheet it opens.
 */
@Composable
fun <F : PhaseRuleForm<F>> PhaseRulesScreen(
    title: String,
    zoneName: String,
    emptyMessage: String,
    createContentDescription: String,
    state: PhaseRulesUiState<F>,
    summary: (RulesetRule) -> String,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onCreate: () -> Unit,
    onEdit: (RulesetRule) -> Unit,
    onSetEnabled: (RulesetRule, Boolean) -> Unit,
    onDelete: (RulesetRule) -> Unit
) {
    CfListScreen(
        title = title,
        subtitle = zoneName,
        onBack = onBack,
        state = state.rules,
        isRefreshing = state.isRefreshing,
        onRefresh = onRefresh,
        emptyMessage = emptyMessage,
        key = { it.id },
        onCreate = onCreate,
        createContentDescription = createContentDescription,
        searchPlaceholder = "Search rules",
        searchMatches = { rule, query ->
            rule.description.orEmpty().contains(query, ignoreCase = true) ||
                rule.expression.contains(query, ignoreCase = true)
        }
    ) { rule ->
        DeletableListRow(
            icon = Icons.AutoMirrored.Filled.Rule,
            title = rule.description?.takeIf { it.isNotBlank() } ?: summary(rule),
            subtitle = if (rule.description.isNullOrBlank()) null else summary(rule),
            detail = rule.expression,
            isDeleting = state.deletingId == rule.id,
            deleteContentDescription = "Delete rule",
            confirmTitle = "Delete rule?",
            confirmText = "This rule will stop applying to traffic immediately. This can't be undone.",
            onDelete = { onDelete(rule) },
            onClick = { onEdit(rule) },
            trailing = {
                Switch(
                    checked = rule.enabled,
                    onCheckedChange = { onSetEnabled(rule, it) }
                )
            }
        )
    }
}

/** The two fields every rules-engine form shares. */
@Composable
fun RuleCommonFields(
    expression: String,
    onExpressionChange: (String) -> Unit,
    description: String,
    onDescriptionChange: (String) -> Unit,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit
) {
    OutlinedTextField(
        value = description,
        onValueChange = onDescriptionChange,
        label = { Text("Description") },
        placeholder = { Text("What this rule is for") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        value = expression,
        onValueChange = onExpressionChange,
        label = { Text("Expression") },
        placeholder = { Text("http.request.uri.path eq \"/old\"") },
        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
        modifier = Modifier.fillMaxWidth()
    )
    Text(
        "Cloudflare's filter expression language decides which requests this rule applies to. \"true\" matches every request.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    LabelledSwitch("Enabled", enabled, onEnabledChange)
}

@Composable
fun LabelledSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** A labelled picker over a fixed set of values, used for status codes and TTL modes. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> RuleDropdown(
    label: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.first == selected }?.second ?: selected.toString()
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, optionLabel) ->
                DropdownMenuItem(
                    text = { Text(optionLabel) },
                    onClick = {
                        onSelect(value)
                        expanded = false
                    }
                )
            }
        }
    }
}

/** The padding every editor sheet uses, so the three families' sheets line up. */
@Composable
fun RuleSheetBody(content: @Composable () -> Unit) {
    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        content()
    }
}

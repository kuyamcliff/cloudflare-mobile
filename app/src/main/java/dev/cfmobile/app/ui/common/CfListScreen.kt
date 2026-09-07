package dev.cfmobile.app.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

/**
 * The shape almost every management screen in this app takes: a back-navigable app bar, an
 * optional create FAB, pull-to-refresh, and a list that renders loading / error / empty states
 * consistently.
 *
 * Screens were each re-implementing this, which is how small inconsistencies (a missing empty
 * state here, a different refresh affordance there) creep in. Anything genuinely different -
 * tabbed screens, settings toggles - still builds its own Scaffold.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> CfListScreen(
    title: String,
    onBack: () -> Unit,
    state: UiState<List<T>>,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    emptyMessage: String,
    key: (T) -> Any,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onCreate: (() -> Unit)? = null,
    createContentDescription: String = "Create",
    /** Set both of these to show a search field above the list. */
    searchPlaceholder: String? = null,
    searchMatches: ((item: T, query: String) -> Boolean)? = null,
    header: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    row: @Composable (T) -> Unit
) {
    var query by remember { mutableStateOf("") }
    // Bound to locals so the null checks below smart-cast, rather than asserting with !!.
    val placeholder = searchPlaceholder
    val matcher = searchMatches

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    if (subtitle != null) {
                        Column {
                            Text(title, style = MaterialTheme.typography.titleMedium)
                            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        Text(title)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = actions
            )
        },
        floatingActionButton = {
            if (onCreate != null) {
                FloatingActionButton(onClick = onCreate) {
                    Icon(Icons.Filled.Add, contentDescription = createContentDescription)
                }
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding)) {
            header?.invoke()
            if (placeholder != null && matcher != null) {
                ListSearchField(value = query, onValueChange = { query = it }, placeholder = placeholder)
            }
            RefreshableStateContent(state = state, isRefreshing = isRefreshing, onRefresh = onRefresh) { allItems ->
                val items = if (matcher != null && query.isNotBlank()) {
                    allItems.filter { matcher(it, query.trim()) }
                } else {
                    allItems
                }
                when {
                    allItems.isEmpty() -> EmptyState(emptyMessage)
                    items.isEmpty() -> EmptyState("Nothing matches \"${query.trim()}\".")
                    else -> LazyColumn(contentPadding = PaddingValues(bottom = if (onCreate != null) 96.dp else 16.dp)) {
                        items(items, key = key) { item ->
                            row(item)
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        }
                    }
                }
            }
        }
    }
}

/**
 * A list row with an icon, a title, optional detail lines, and a delete action behind a
 * confirmation dialog. Destructive confirmations always name the thing being deleted (PRD §49)
 * and say whether it can be undone.
 */
@Composable
fun DeletableListRow(
    icon: ImageVector,
    title: String,
    isDeleting: Boolean,
    deleteContentDescription: String,
    confirmTitle: String,
    confirmText: String,
    onDelete: () -> Unit,
    subtitle: String? = null,
    detail: String? = null,
    monospaceTitle: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    var confirmDelete by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(16.dp, 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontFamily = if (monospaceTitle) FontFamily.Monospace else null
            )
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            detail?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        trailing?.invoke()
        if (isDeleting) {
            CircularProgressIndicator(Modifier.padding(4.dp))
        } else {
            IconButton(onClick = { confirmDelete = true }) {
                Icon(Icons.Filled.Delete, contentDescription = deleteContentDescription, tint = MaterialTheme.colorScheme.error)
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(confirmTitle) },
            text = { Text(confirmText) },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
}

/** A read-only list row - same layout as [DeletableListRow] without the delete affordance. */
@Composable
fun ReadOnlyListRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    detail: String? = null,
    monospaceTitle: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(16.dp, 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontFamily = if (monospaceTitle) FontFamily.Monospace else null
            )
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            detail?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        trailing?.invoke()
    }
}

/** The form sheet footer every create/edit bottom sheet uses. */
@Composable
fun FormActions(
    isSaving: Boolean,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    saveLabel: String = "Create",
    modifier: Modifier = Modifier
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = onCancel) { Text("Cancel") }
        TextButton(onClick = onSave, enabled = !isSaving) {
            if (isSaving) CircularProgressIndicator(Modifier.padding(end = 6.dp))
            Text(saveLabel)
        }
    }
}

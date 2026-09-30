package dev.cfmobile.app.ui.graphql

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cfmobile.app.data.local.db.SavedRequestEntity
import dev.cfmobile.app.ui.components.Badge
import dev.cfmobile.app.ui.components.SectionHeader
import dev.cfmobile.app.ui.components.copyToClipboard
import dev.cfmobile.app.ui.components.jsonColors
import dev.cfmobile.app.ui.components.jsonLines
import dev.cfmobile.app.ui.theme.StatusColors
import kotlinx.coroutines.flow.Flow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GraphQlScreen(viewModel: GraphQlViewModel, saved: Flow<List<SavedRequestEntity>>, onBack: () -> Unit) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val savedQueries by saved.collectAsState(initial = emptyList())
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var menu by remember { mutableStateOf(false) }
    var saveOpen by remember { mutableStateOf(false) }
    var savedOpen by remember { mutableStateOf(false) }
    var resultTab by remember { mutableIntStateOf(0) }
    val colors = jsonColors()

    LaunchedEffect(ui.notice) { ui.notice?.let { snackbar.showSnackbar(it); viewModel.dismissNotice() } }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("GraphQL Analytics") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More actions") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Saved queries") }, onClick = { menu = false; savedOpen = true })
                        DropdownMenuItem(text = { Text("Save query") }, onClick = { menu = false; saveOpen = true })
                        DropdownMenuItem(text = { Text("Copy query") }, onClick = { menu = false; copyToClipboard(context, "query", ui.query) })
                        if (ui.rawBody.isNotEmpty()) DropdownMenuItem(text = { Text("Copy response") }, onClick = { menu = false; copyToClipboard(context, "response", ui.rawBody) })
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize().imePadding()) {
            item("templates") {
                SectionHeader("Dataset")
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    GraphQlTemplates.all.forEach { t ->
                        FilterChip(selected = ui.templateId == t.id, onClick = { viewModel.applyTemplate(t) }, label = { Text(t.title) })
                    }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val max = GraphQlTemplates.all.firstOrNull { it.id == ui.templateId }?.maxRange
                    TimeRange.entries.forEach { r ->
                        FilterChip(
                            selected = ui.range == r,
                            enabled = max == null || r.minutes <= max.minutes,
                            onClick = { viewModel.setRange(r) },
                            label = { Text(r.label) }
                        )
                    }
                }
            }
            item("editor") {
                SectionHeader("Query")
                OutlinedTextField(
                    value = ui.query, onValueChange = viewModel::setQuery,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp).padding(horizontal = 16.dp)
                )
                SectionHeader("Variables")
                OutlinedTextField(
                    value = ui.variables, onValueChange = viewModel::setVariables,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp).padding(horizontal = 16.dp)
                )
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ui.validation?.let { Text(it, color = StatusColors.warning, style = MaterialTheme.typography.bodySmall) }
                    Text(
                        "Cloudflare allows 300 GraphQL queries per 5 minutes by default. Templates use explicit limits and bounded time ranges.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (ui.isRunning) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            CircularProgressIndicator(Modifier.padding(4.dp))
                            OutlinedButton(onClick = viewModel::cancel) { Text("Cancel") }
                        }
                    } else {
                        Button(onClick = viewModel::run, modifier = Modifier.fillMaxWidth()) { Text("Run query") }
                    }
                }
            }
            if (ui.status != null || ui.errors.isNotEmpty()) {
                item("result-head") {
                    SectionHeader("Result")
                    Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ui.status?.let { Badge("HTTP $it", if (it in 200..299 && ui.errors.isEmpty()) StatusColors.success else StatusColors.error) }
                        ui.durationMillis?.let { Text("$it ms", style = MaterialTheme.typography.bodySmall) }
                    }
                    ui.errors.forEach { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)) }
                    PrimaryTabRow(selectedTabIndex = resultTab) {
                        Tab(selected = resultTab == 0, onClick = { resultTab = 0 }, text = { Text("Table") })
                        Tab(selected = resultTab == 1, onClick = { resultTab = 1 }, text = { Text("JSON") })
                    }
                }
                if (resultTab == 0) {
                    val table = ui.table
                    if (table == null || table.rows.isEmpty()) {
                        item("no-rows") { Text("No rows returned.", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium) }
                    } else {
                        item("table") { ResultTable(table) }
                    }
                } else {
                    jsonLines(ui.lines, colors, "")
                }
            }
        }
    }

    if (saveOpen) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { saveOpen = false },
            title = { Text("Save query") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true) },
            confirmButton = { TextButton(onClick = { saveOpen = false; viewModel.save(name) }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { saveOpen = false }) { Text("Cancel") } }
        )
    }
    if (savedOpen) {
        AlertDialog(
            onDismissRequest = { savedOpen = false },
            title = { Text("Saved queries") },
            text = {
                if (savedQueries.isEmpty()) Text("No saved queries yet.")
                else LazyColumn(Modifier.heightIn(max = 400.dp)) {
                    items(savedQueries, key = { it.id }) { q ->
                        TextButton(onClick = { savedOpen = false; viewModel.load(q) }, modifier = Modifier.fillMaxWidth()) { Text(q.name) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { savedOpen = false }) { Text("Close") } }
        )
    }
}

@Composable
private fun ResultTable(table: GraphQlTable) {
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
        table.columns.forEachIndexed { ci, column ->
            Column(Modifier.width(maxOf(96, minOf(240, (listOf(column) + table.rows.take(50).map { it[ci] }).maxOf { it.length } * 8)).dp)) {
                Text(column, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
                table.rows.take(500).forEach { row ->
                    Text(row[ci], style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, maxLines = 1, modifier = Modifier.padding(vertical = 2.dp))
                }
            }
        }
    }
    if (table.rows.size > 500) Text("Showing 500 of ${table.rows.size} rows. The JSON tab has the full result.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp))
}

package dev.cfmobile.app.ui.d1

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cfmobile.app.data.remote.dto.D1QueryResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun D1ConsoleScreen(databaseName: String, viewModel: D1ConsoleViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmMutation by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("SQL console", style = MaterialTheme.typography.titleMedium)
                        Text(databaseName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = uiState.sql,
                onValueChange = viewModel::updateSql,
                label = { Text("SQL") },
                placeholder = { Text("SELECT * FROM users LIMIT 10") },
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 240.dp)
            )
            uiState.error?.let { error ->
                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        // Anything that writes gets a confirmation first: D1 has no undo.
                        if (isMutatingSql(uiState.sql)) confirmMutation = true else viewModel.run()
                    },
                    enabled = !uiState.isRunning
                ) {
                    if (uiState.isRunning) CircularProgressIndicator(Modifier.padding(end = 6.dp))
                    Text(if (uiState.isRunning) "Running…" else "Run")
                }
                if (uiState.results.isNotEmpty()) {
                    TextButton(onClick = viewModel::clear) { Text("Clear results") }
                }
            }
            uiState.results.forEachIndexed { index, result ->
                ResultBlock(index = index, total = uiState.results.size, result = result)
            }
        }
    }

    if (confirmMutation) {
        AlertDialog(
            onDismissRequest = { confirmMutation = false },
            title = { Text("Run a statement that changes data?") },
            text = {
                Text("This looks like it writes to the database. D1 has no undo - make sure you have a backup if it matters.")
            },
            confirmButton = {
                TextButton(onClick = { confirmMutation = false; viewModel.run() }) { Text("Run") }
            },
            dismissButton = { TextButton(onClick = { confirmMutation = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun ResultBlock(index: Int, total: Int, result: D1QueryResult) {
    val columns = columnsOf(result)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            if (total > 1) "Statement ${index + 1} · ${resultSummary(result)}" else resultSummary(result),
            style = MaterialTheme.typography.labelLarge,
            color = if (result.success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        )
        when {
            columns.isEmpty() -> Text(
                "No rows returned.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // A wide result scrolls sideways rather than squeezing columns into unreadable
            // slivers on a phone.
            else -> Column(Modifier.horizontalScroll(rememberScrollState())) {
                Row {
                    columns.forEach { column ->
                        Text(
                            column,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(140.dp).padding(end = 8.dp)
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                result.results.orEmpty().forEach { row ->
                    Row(Modifier.padding(vertical = 4.dp)) {
                        columns.forEach { column ->
                            Text(
                                formatCell(row[column]),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.width(140.dp).padding(end = 8.dp)
                            )
                        }
                    }
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    }
}

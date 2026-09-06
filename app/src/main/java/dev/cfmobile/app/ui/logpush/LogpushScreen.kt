package dev.cfmobile.app.ui.logpush

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cfmobile.app.ui.common.CfListScreen
import dev.cfmobile.app.ui.common.DeletableListRow

@Composable
fun LogpushScreen(viewModel: LogpushViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    CfListScreen(
        title = "Logpush",
        onBack = onBack,
        state = uiState.jobs,
        isRefreshing = uiState.isRefreshing,
        onRefresh = viewModel::refresh,
        emptyMessage = "No Logpush jobs yet",
        key = { it.id },
        searchPlaceholder = "Search jobs",
        searchMatches = { job, query ->
            job.name.orEmpty().contains(query, ignoreCase = true) ||
                job.dataset.orEmpty().contains(query, ignoreCase = true)
        },
        header = {
            Text(
                "Creating a job needs a destination string containing storage credentials, so new jobs are set up in the dashboard or API - here you can pause, resume, or remove them.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    ) { job ->
        val title = job.name?.takeIf { it.isNotBlank() } ?: "Job ${job.id}"
        DeletableListRow(
            icon = Icons.Filled.Upload,
            title = title,
            subtitle = listOfNotNull(job.dataset, redactDestination(job.destinationConf)).joinToString(" → ").ifBlank { null },
            detail = job.errorMessage?.takeIf { it.isNotBlank() }?.let { "Last error: $it" }
                ?: job.lastComplete?.let { "Last delivered $it" },
            isDeleting = uiState.deletingId == job.id,
            deleteContentDescription = "Delete job",
            confirmTitle = "Delete Logpush job?",
            confirmText = "\"$title\" will be permanently deleted and logs will stop being delivered to its destination. This can't be undone.",
            onDelete = { viewModel.delete(job) },
            trailing = {
                if (uiState.togglingId == job.id) {
                    CircularProgressIndicator(Modifier.padding(4.dp))
                } else {
                    Switch(checked = job.enabled, onCheckedChange = { viewModel.setEnabled(job, it) })
                }
            }
        )
    }
}

package dev.cfmobile.app.ui.account

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
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
fun NotificationsScreen(viewModel: NotificationsViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    CfListScreen(
        title = "Notifications",
        onBack = onBack,
        state = uiState.policies,
        isRefreshing = uiState.isRefreshing,
        onRefresh = viewModel::refresh,
        emptyMessage = "No notification policies on this account",
        key = { it.id },
        searchPlaceholder = "Search notifications",
        searchMatches = { policy, query ->
            policy.name.contains(query, ignoreCase = true) ||
                alertTypeLabel(policy.alertType).contains(query, ignoreCase = true)
        },
        header = {
            uiState.error?.let { error ->
                Text(
                    error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(16.dp, 4.dp)
                )
            }
        }
    ) { policy ->
        DeletableListRow(
            icon = Icons.Filled.NotificationsActive,
            title = policy.name.ifBlank { alertTypeLabel(policy.alertType) },
            subtitle = alertTypeLabel(policy.alertType),
            detail = mechanismSummary(policy),
            isDeleting = uiState.deletingId == policy.id,
            deleteContentDescription = "Delete notification",
            confirmTitle = "Delete notification?",
            confirmText = "\"${policy.name}\" will stop alerting anyone. Disabling it instead keeps the configuration.",
            onDelete = { viewModel.delete(policy) },
            trailing = {
                if (uiState.busyId == policy.id) {
                    CircularProgressIndicator(Modifier.padding(4.dp))
                } else {
                    Switch(
                        checked = policy.enabled,
                        onCheckedChange = { viewModel.setEnabled(policy, it) }
                    )
                }
            }
        )
    }
}

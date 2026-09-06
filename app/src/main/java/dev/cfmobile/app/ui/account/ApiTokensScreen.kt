package dev.cfmobile.app.ui.account

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cfmobile.app.ui.common.CfListScreen
import dev.cfmobile.app.ui.common.DeletableListRow

@Composable
fun ApiTokensScreen(viewModel: ApiTokensViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    CfListScreen(
        title = "API Tokens",
        onBack = onBack,
        state = uiState.tokens,
        isRefreshing = uiState.isRefreshing,
        onRefresh = viewModel::refresh,
        emptyMessage = "No API tokens on this account",
        key = { it.id },
        searchPlaceholder = "Search tokens",
        searchMatches = { token, query -> token.name.contains(query, ignoreCase = true) },
        header = {
            Text(
                "Token values are never shown here - Cloudflare returns one only when it's created. Creating or rolling a token needs the dashboard.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp, 4.dp)
            )
            uiState.error?.let { error ->
                Text(
                    error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(16.dp, 4.dp)
                )
            }
        }
    ) { token ->
        DeletableListRow(
            icon = Icons.Filled.VpnKey,
            title = token.name,
            subtitle = apiTokenSummary(token),
            detail = token.issuedOn?.let { "Issued $it" },
            isDeleting = uiState.deletingId == token.id,
            deleteContentDescription = "Revoke token",
            confirmTitle = "Revoke this token?",
            // The token signing this session in is in this list too, and revoking it signs the
            // app out - worth saying before, not after.
            confirmText = "Anything using \"${token.name}\" stops working immediately, including this app if it's the token you signed in with. This can't be undone.",
            onDelete = { viewModel.revoke(token) }
        )
    }
}

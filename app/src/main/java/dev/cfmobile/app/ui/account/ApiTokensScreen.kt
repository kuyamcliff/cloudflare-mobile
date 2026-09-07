package dev.cfmobile.app.ui.account

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cfmobile.app.ui.common.CfListScreen
import dev.cfmobile.app.ui.common.DeletableListRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiTokensScreen(viewModel: ApiTokensViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isAccount = uiState.scope == ApiTokenScope.ACCOUNT

    CfListScreen(
        title = "API Tokens",
        onBack = onBack,
        state = if (isAccount) uiState.accountTokens else uiState.tokens,
        isRefreshing = uiState.isRefreshing,
        onRefresh = viewModel::refresh,
        emptyMessage = if (isAccount) "No account-owned tokens" else "No tokens on your profile",
        key = { it.id },
        searchPlaceholder = "Search tokens",
        searchMatches = { token, query -> token.name.contains(query, ignoreCase = true) },
        header = {
            PrimaryTabRow(selectedTabIndex = uiState.scope.ordinal) {
                ApiTokenScope.entries.forEach { scope ->
                    Tab(
                        selected = uiState.scope == scope,
                        onClick = { viewModel.selectScope(scope) },
                        text = { Text(scope.label) }
                    )
                }
            }
            Text(
                if (isAccount) {
                    "Tokens owned by the account itself - they keep working after the person who made them leaves. Values are never shown here; Cloudflare returns one only when it's created."
                } else {
                    "Tokens on your own profile. Values are never shown here - Cloudflare returns one only when it's created. Creating or rolling a token needs the dashboard."
                },
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

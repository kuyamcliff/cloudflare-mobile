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

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ApiTokensScreen(
    viewModel: ApiTokensViewModel,
    onBack: () -> Unit,
    onOpen: (token: dev.cfmobile.app.data.remote.dto.ApiToken, accountOwned: Boolean) -> Unit,
    onCreate: (accountOwned: Boolean) -> Unit
) {
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
        onCreate = { onCreate(isAccount) },
        createContentDescription = "Create token",
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
                    "Tokens owned by the account itself. They keep working after the person who made them leaves. Cloudflare shows a secret only when a token is created or rolled."
                } else {
                    "Tokens on your own profile. Cloudflare shows a secret only when a token is created or rolled."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp, 4.dp)
            )
            val list = (if (isAccount) uiState.accountTokens else uiState.tokens) as? dev.cfmobile.app.ui.common.UiState.Data
            list?.value?.let { tokens -> TokenSecuritySummary(tokens) }
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
            onDelete = { viewModel.revoke(token) },
            onClick = { onOpen(token, isAccount) }
        )
    }
}

/** Counts from documented token attributes (spec 57). Observations, not a score. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun TokenSecuritySummary(tokens: List<dev.cfmobile.app.data.remote.dto.ApiToken>) {
    if (tokens.isEmpty()) return
    val now = java.time.Instant.now()
    fun expiry(t: dev.cfmobile.app.data.remote.dto.ApiToken) = t.expiresOn?.let { runCatching { java.time.Instant.parse(it) }.getOrNull() }
    val active = tokens.count { it.status == "active" }
    val expired = tokens.count { expiry(it)?.isBefore(now) == true }
    val soon = tokens.count { expiry(it)?.let { e -> e.isAfter(now) && e.isBefore(now.plus(7, java.time.temporal.ChronoUnit.DAYS)) } == true }
    val never = tokens.count { it.expiresOn == null }
    val noIp = tokens.count { it.condition?.requestIp?.allowed.isNullOrEmpty() }
    androidx.compose.foundation.layout.FlowRow(
        Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)
    ) {
        dev.cfmobile.app.ui.components.Badge("$active active", dev.cfmobile.app.ui.theme.StatusColors.success)
        if (soon > 0) dev.cfmobile.app.ui.components.Badge("$soon expiring soon", dev.cfmobile.app.ui.theme.StatusColors.warning)
        if (expired > 0) dev.cfmobile.app.ui.components.Badge("$expired expired", dev.cfmobile.app.ui.theme.StatusColors.error)
        if (never > 0) dev.cfmobile.app.ui.components.Badge("$never never expire", MaterialTheme.colorScheme.onSurfaceVariant)
        if (noIp > 0) dev.cfmobile.app.ui.components.Badge("$noIp without IP restriction", MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

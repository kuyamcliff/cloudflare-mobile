package dev.cfmobile.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.cfmobile.app.core.errors.ClassifiedError
import dev.cfmobile.app.core.errors.RecoveryAction
import kotlinx.coroutines.delay

@Composable
fun <T> StateContent(
    state: UiState<T>,
    onRetry: () -> Unit = {},
    onReauthenticate: (() -> Unit)? = null,
    content: @Composable (T) -> Unit
) {
    when (state) {
        is UiState.Loading -> FullScreenLoading()
        is UiState.Error -> FullScreenError(state.error, onRetry, onReauthenticate)
        is UiState.Data -> content(state.value)
    }
}

/** Every zone-scoped screen's app bar title, so the target zone is always visible - not just
 *  in destructive confirmations but as ambient context while browsing (PRD §49). */
@Composable
fun ZoneScopedTitle(title: String, zoneName: String) {
    Column {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(zoneName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun FullScreenLoading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

/** Renders Cloudflare's own error text (PRD §35.1: never a generic "Something went wrong")
 *  plus whichever recovery actions actually apply to this failure - a validation error gets
 *  no retry button, since retrying an invalid request just fails the same way again. */
@Composable
fun FullScreenError(
    error: ClassifiedError,
    onRetry: () -> Unit,
    onReauthenticate: (() -> Unit)? = null
) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = error.message,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.error
            )
            if (RecoveryAction.RETRY in error.recoveryActions || RecoveryAction.REFRESH in error.recoveryActions) {
                Button(onClick = onRetry) { Text("Retry") }
            }
            if (onReauthenticate != null) {
                if (RecoveryAction.REAUTHENTICATE in error.recoveryActions) {
                    OutlinedButton(onClick = onReauthenticate) { Text("Reconnect account") }
                } else if (RecoveryAction.OPEN_TOKEN_PERMISSIONS in error.recoveryActions) {
                    OutlinedButton(onClick = onReauthenticate) { Text("Manage token") }
                }
            }
        }
    }
}

/** Back-compat overload for call sites that only have a plain message (e.g. inline form
 *  errors that never went through [dev.cfmobile.app.core.errors.ErrorClassifier]). */
@Composable
fun FullScreenError(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.error
            )
            Button(onClick = onRetry) { Text("Retry") }
        }
    }
}

/** PRD §86: every screen showing fetched-not-live data says how stale it is, rather than
 *  implying it's a live view. Ticks once a second while composed so "Updated 3s ago" keeps
 *  advancing without requiring a manual refresh. */
@Composable
fun FreshnessLabel(lastUpdatedAtMillis: Long?, modifier: Modifier = Modifier) {
    if (lastUpdatedAtMillis == null) return
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffectTicker { now = System.currentTimeMillis() }
    Text(
        formatFreshness(lastUpdatedAtMillis, now),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}

@Composable
private fun LaunchedEffectTicker(onTick: () -> Unit) {
    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            onTick()
        }
    }
}

/** Pure so it's testable without Compose - see FreshnessLabelTest. */
fun formatFreshness(lastUpdatedAtMillis: Long, nowMillis: Long): String {
    val seconds = ((nowMillis - lastUpdatedAtMillis) / 1000).coerceAtLeast(0)
    return when {
        seconds < 5 -> "Updated just now"
        seconds < 60 -> "Updated ${seconds}s ago"
        seconds < 3600 -> "Updated ${seconds / 60}m ago"
        seconds < 86400 -> "Updated ${seconds / 3600}h ago"
        else -> "Updated ${seconds / 86400}d ago"
    }
}

@Composable
fun EmptyState(message: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

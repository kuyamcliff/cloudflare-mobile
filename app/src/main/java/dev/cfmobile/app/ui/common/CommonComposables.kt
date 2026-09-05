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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.cfmobile.app.core.errors.ClassifiedError
import dev.cfmobile.app.core.errors.RecoveryAction

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

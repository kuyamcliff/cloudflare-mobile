package dev.cfmobile.app.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * [StateContent] plus pull-to-refresh. Loading and error states still render through
 * [StateContent] (so a first load looks the same everywhere); the pull gesture is for
 * refreshing content that is already on screen.
 *
 * [isRefreshing] should be false while the *first* load is running - otherwise the spinner and
 * the full-screen loading state both appear at once.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> RefreshableStateContent(
    state: UiState<T>,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    onReauthenticate: (() -> Unit)? = null,
    content: @Composable (T) -> Unit
) {
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize()
    ) {
        StateContent(state = state, onRetry = onRefresh, onReauthenticate = onReauthenticate) { value ->
            // PullToRefreshBox needs a scrollable child for the gesture to arm, which every
            // caller here provides (a LazyColumn) - a non-scrolling child still renders fine,
            // it just can't be pulled.
            Box(Modifier.fillMaxSize()) { content(value) }
        }
    }
}

/** The search field used above long lists. Kept here so every list filters and clears the
 *  same way rather than each screen inventing its own affordance. */
@Composable
fun ListSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(Icons.Filled.Cancel, contentDescription = "Clear search")
                }
            }
        },
        singleLine = true,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

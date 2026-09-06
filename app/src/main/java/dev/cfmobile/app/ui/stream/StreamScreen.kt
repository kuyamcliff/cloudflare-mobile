package dev.cfmobile.app.ui.stream

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cfmobile.app.ui.common.CfListScreen
import dev.cfmobile.app.ui.common.DeletableListRow

@Composable
fun StreamScreen(viewModel: StreamViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    CfListScreen(
        title = "Stream",
        onBack = onBack,
        state = uiState.videos,
        isRefreshing = uiState.isRefreshing,
        onRefresh = viewModel::refresh,
        emptyMessage = "No videos yet",
        key = { it.uid },
        searchPlaceholder = "Search videos",
        searchMatches = { video, query ->
            streamVideoTitle(video).contains(query, ignoreCase = true) || video.uid.contains(query, ignoreCase = true)
        }
    ) { video ->
        val title = streamVideoTitle(video)
        val state = video.status?.state?.replaceFirstChar { it.uppercase() }
        val subtitle = listOfNotNull(state, formatDuration(video.duration)).joinToString(" · ").ifBlank { null }

        DeletableListRow(
            icon = Icons.Filled.Videocam,
            title = title,
            subtitle = subtitle,
            detail = video.status?.errorReasonText ?: video.created?.let { "Uploaded $it" },
            isDeleting = uiState.deletingId == video.uid,
            deleteContentDescription = "Delete video",
            confirmTitle = "Delete video?",
            confirmText = "\"$title\" will be permanently deleted and stop playing anywhere it's embedded. This can't be undone.",
            onDelete = { viewModel.delete(video) }
        )
    }
}

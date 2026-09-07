package dev.cfmobile.app.ui.stream

import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cfmobile.app.ui.common.CfListScreen
import dev.cfmobile.app.ui.common.DeletableListRow
import dev.cfmobile.app.ui.common.UploadStatus
import dev.cfmobile.app.ui.common.rememberMediaPicker

@Composable
fun StreamScreen(viewModel: StreamViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pickVideo = rememberMediaPicker(
        type = ActivityResultContracts.PickVisualMedia.VideoOnly,
        fallbackName = "video",
        onPicked = viewModel::upload
    )

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
        },
        onCreate = pickVideo.takeIf { !uiState.isUploading },
        createContentDescription = "Upload a video",
        header = {
            UploadStatus(
                isUploading = uiState.isUploading,
                // The whole file goes in one request, so there is no progress to report - say
                // that rather than showing a bar that doesn't move.
                uploadingLabel = "Uploading… this can take a while for a large video",
                error = uiState.uploadError,
                doneName = uiState.uploadedName,
                doneSuffix = "uploaded - Cloudflare is encoding it now"
            )
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

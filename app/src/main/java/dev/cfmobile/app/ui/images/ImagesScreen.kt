package dev.cfmobile.app.ui.images

import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cfmobile.app.ui.common.CfListScreen
import dev.cfmobile.app.ui.common.DeletableListRow
import dev.cfmobile.app.ui.common.UploadStatus
import dev.cfmobile.app.ui.common.rememberMediaPicker

@Composable
fun ImagesScreen(viewModel: ImagesViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val usage = imagesUsageLabel(uiState.stats)
    // Android's photo picker: no storage permission, and it only ever hands back the one file
    // the user chose.
    val pickImage = rememberMediaPicker(
        type = ActivityResultContracts.PickVisualMedia.ImageOnly,
        fallbackName = "image",
        onPicked = viewModel::upload
    )

    CfListScreen(
        title = "Images",
        onBack = onBack,
        state = uiState.images,
        isRefreshing = uiState.isRefreshing,
        onRefresh = viewModel::refresh,
        emptyMessage = "No images yet",
        key = { it.id },
        searchPlaceholder = "Search images",
        searchMatches = { image, query ->
            image.filename.orEmpty().contains(query, ignoreCase = true) || image.id.contains(query, ignoreCase = true)
        },
        onCreate = pickImage.takeIf { !uiState.isUploading },
        createContentDescription = "Upload an image",
        header = {
            if (usage != null) {
                Text(
                    usage,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
            UploadStatus(
                isUploading = uiState.isUploading,
                uploadingLabel = "Uploading…",
                error = uiState.uploadError,
                doneName = uiState.uploadedName
            )
        }
    ) { image ->
        val title = image.filename?.takeIf { it.isNotBlank() } ?: image.id
        DeletableListRow(
            icon = Icons.Filled.Image,
            title = title,
            subtitle = image.uploaded?.let { "Uploaded $it" },
            detail = image.variants?.size?.let { "$it variant${if (it == 1) "" else "s"}" },
            isDeleting = uiState.deletingId == image.id,
            deleteContentDescription = "Delete image",
            confirmTitle = "Delete image?",
            confirmText = "\"$title\" will be permanently deleted and stop loading anywhere it's used. This can't be undone.",
            onDelete = { viewModel.delete(image) }
        )
    }
}

package dev.cfmobile.app.ui.images

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

@Composable
fun ImagesScreen(viewModel: ImagesViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val usage = imagesUsageLabel(uiState.stats)

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
        header = {
            if (usage != null) {
                Text(
                    usage,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
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

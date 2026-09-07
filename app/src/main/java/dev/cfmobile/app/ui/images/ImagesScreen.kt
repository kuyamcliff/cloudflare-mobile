package dev.cfmobile.app.ui.images

import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PhotoSizeSelectLarge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cfmobile.app.ui.common.CfListScreen
import dev.cfmobile.app.ui.common.DeletableListRow
import dev.cfmobile.app.ui.common.FormActions
import dev.cfmobile.app.ui.common.ReadOnlyListRow
import dev.cfmobile.app.ui.common.ToggleRow
import dev.cfmobile.app.ui.common.UploadStatus
import dev.cfmobile.app.ui.common.rememberMediaPicker
import dev.cfmobile.app.ui.rules.RuleDropdown

@OptIn(ExperimentalMaterial3Api::class)
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

    val header: @Composable () -> Unit = {
        PrimaryTabRow(selectedTabIndex = uiState.tab.ordinal) {
            ImagesTab.entries.forEach { tab ->
                Tab(
                    selected = uiState.tab == tab,
                    onClick = { viewModel.selectTab(tab) },
                    text = { Text(tab.label) }
                )
            }
        }
        uiState.error?.let { error ->
            Text(
                error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(16.dp, 4.dp)
            )
        }
    }

    when (uiState.tab) {
        ImagesTab.IMAGES -> CfListScreen(
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
                header()
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

        ImagesTab.VARIANTS -> CfListScreen(
            title = "Images",
            onBack = onBack,
            state = uiState.variants,
            isRefreshing = uiState.isRefreshing,
            onRefresh = viewModel::refresh,
            emptyMessage = "No named variants yet",
            key = { it.id },
            onCreate = viewModel::openVariantForm,
            createContentDescription = "Create variant",
            header = {
                header()
                Text(
                    // The variant name is the last path segment of every delivery URL.
                    "Named sizes a delivery URL can ask for. Editing an existing variant isn't offered - changing one silently reshapes every image already served through it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp, 8.dp)
                )
            }
        ) { variant ->
            DeletableListRow(
                icon = Icons.Filled.PhotoSizeSelectLarge,
                title = variant.id,
                monospaceTitle = true,
                subtitle = variantSummary(variant),
                detail = variant.options?.metadata?.let { "Metadata $it" },
                isDeleting = uiState.deletingId == variant.id,
                deleteContentDescription = "Delete variant",
                confirmTitle = "Delete this variant?",
                confirmText = "Every delivery URL ending in /${variant.id} stops resolving, so images served through it break wherever they're embedded.",
                onDelete = { viewModel.deleteVariant(variant) }
            )
        }

        ImagesTab.KEYS -> CfListScreen(
            title = "Images",
            onBack = onBack,
            state = uiState.signingKeys,
            isRefreshing = uiState.isRefreshing,
            onRefresh = viewModel::refresh,
            emptyMessage = "No signing keys",
            key = { it.name },
            header = {
                header()
                Text(
                    // The value signs private delivery URLs, so it is dropped before it reaches
                    // the UI at all - see ImagesRepository.listSigningKeys.
                    "Keys that sign delivery URLs for private images. Names only: a key's value is never read by this app, and rotating one is done from the dashboard.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp, 8.dp)
                )
            }
        ) { key ->
            ReadOnlyListRow(
                icon = Icons.Filled.Key,
                title = key.name,
                monospaceTitle = true,
                subtitle = "Value not shown"
            )
        }
    }

    uiState.variantForm?.let { form ->
        VariantSheet(form, onDismiss = viewModel::closeVariantForm, viewModel = viewModel)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VariantSheet(
    form: VariantFormState,
    onDismiss: () -> Unit,
    viewModel: ImagesViewModel
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Create variant", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = form.id,
                onValueChange = { v -> viewModel.updateVariantForm { it.copy(id = v) } },
                label = { Text("Variant name") },
                placeholder = { Text("thumbnail") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = form.width,
                    onValueChange = { v -> viewModel.updateVariantForm { it.copy(width = v) } },
                    label = { Text("Width") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = form.height,
                    onValueChange = { v -> viewModel.updateVariantForm { it.copy(height = v) } },
                    label = { Text("Height") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            }
            RuleDropdown(
                label = "Fit",
                options = VariantFit.entries.map { it to it.label },
                selected = form.fit,
                onSelect = { v -> viewModel.updateVariantForm { it.copy(fit = v) } }
            )
            Text(
                form.fit.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ToggleRow(
                title = "Keep metadata",
                subtitle = "Preserve EXIF, including any location the camera recorded",
                checked = form.keepMetadata,
                isSaving = false,
                onToggle = { v -> viewModel.updateVariantForm { it.copy(keepMetadata = v) } }
            )
            ToggleRow(
                title = "Serve without a signed URL",
                subtitle = "Anyone with the link can fetch this size, even for private images",
                checked = form.neverRequireSignedUrls,
                isSaving = false,
                onToggle = { v -> viewModel.updateVariantForm { it.copy(neverRequireSignedUrls = v) } }
            )
            Text(
                "The name becomes the last segment of every delivery URL using this size and can't be changed afterwards.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (form.error != null) {
                Text(form.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            FormActions(isSaving = form.isSaving, onCancel = onDismiss, onSave = viewModel::saveVariant, saveLabel = "Create")
        }
    }
}

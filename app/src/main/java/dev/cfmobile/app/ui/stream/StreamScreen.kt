package dev.cfmobile.app.ui.stream

import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cfmobile.app.data.remote.dto.StreamProtocolEndpoint
import dev.cfmobile.app.ui.common.CfListScreen
import dev.cfmobile.app.ui.common.CopyIconButton
import dev.cfmobile.app.ui.common.DeletableListRow
import dev.cfmobile.app.ui.common.FormActions
import dev.cfmobile.app.ui.common.ToggleRow
import dev.cfmobile.app.ui.common.UploadStatus
import dev.cfmobile.app.ui.common.rememberMediaPicker

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreamScreen(viewModel: StreamViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pickVideo = rememberMediaPicker(
        type = ActivityResultContracts.PickVisualMedia.VideoOnly,
        fallbackName = "video",
        onPicked = viewModel::upload
    )

    val header: @Composable () -> Unit = {
        PrimaryTabRow(selectedTabIndex = uiState.tab.ordinal) {
            StreamTab.entries.forEach { tab ->
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
        StreamTab.VIDEOS -> CfListScreen(
            title = "Stream",
            onBack = onBack,
            state = uiState.videos,
            isRefreshing = uiState.isRefreshing,
            onRefresh = viewModel::refresh,
            emptyMessage = "No videos yet",
            key = { it.uid },
            searchPlaceholder = "Search videos",
            searchMatches = { video, query ->
                streamVideoTitle(video).contains(query, ignoreCase = true) ||
                    video.uid.contains(query, ignoreCase = true)
            },
            onCreate = pickVideo.takeIf { !uiState.isUploading },
            createContentDescription = "Upload a video",
            header = {
                header()
                UploadStatus(
                    isUploading = uiState.isUploading,
                    // The whole file goes in one request, so there is no progress to report -
                    // say that rather than showing a bar that doesn't move.
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
                onDelete = { viewModel.delete(video) },
                trailing = {
                    IconButton(onClick = { viewModel.openCaptions(video) }) {
                        Icon(Icons.Filled.ClosedCaption, contentDescription = "Captions for $title")
                    }
                }
            )
        }

        StreamTab.LIVE -> CfListScreen(
            title = "Stream",
            onBack = onBack,
            state = uiState.liveInputs,
            isRefreshing = uiState.isRefreshing,
            onRefresh = viewModel::refresh,
            emptyMessage = "No live inputs yet",
            key = { it.uid },
            onCreate = viewModel::openLiveInputForm,
            createContentDescription = "Create live input",
            header = {
                header()
                Text(
                    // The push credential is the reason to open one of these on a phone.
                    "The endpoint a broadcaster pushes to. Tap an input for its RTMPS and SRT URLs and stream key.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp, 8.dp)
                )
            }
        ) { input ->
            val name = liveInputName(input)
            DeletableListRow(
                icon = Icons.Filled.Podcasts,
                title = name,
                subtitle = liveInputSummary(input),
                detail = input.created?.let { "Created $it" },
                isDeleting = uiState.deletingId == input.uid,
                deleteContentDescription = "Delete live input",
                confirmTitle = "Delete this live input?",
                confirmText = "Its stream key stops working immediately, so a broadcaster using \"$name\" is cut off. Recordings already made are kept as videos.",
                onDelete = { viewModel.deleteLiveInput(input) },
                onClick = { viewModel.openLiveInput(input) }
            )
        }

        StreamTab.WATERMARKS -> CfListScreen(
            title = "Stream",
            onBack = onBack,
            state = uiState.watermarks,
            isRefreshing = uiState.isRefreshing,
            onRefresh = viewModel::refresh,
            emptyMessage = "No watermark profiles",
            key = { it.uid },
            header = {
                header()
                Text(
                    // Creating one uploads an image and is applied at upload time only.
                    "Profiles burned into a video as it uploads. Adding one means uploading a logo, which is done from the dashboard; an existing profile can be removed here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp, 8.dp)
                )
            }
        ) { watermark ->
            DeletableListRow(
                icon = Icons.Filled.WaterDrop,
                title = watermark.name ?: watermark.uid,
                subtitle = watermarkSummary(watermark),
                detail = watermark.downloadedFrom,
                isDeleting = uiState.deletingId == watermark.uid,
                deleteContentDescription = "Delete watermark",
                confirmTitle = "Delete this watermark?",
                confirmText = "Videos already uploaded with it keep the mark - it's burned in. New uploads can no longer use this profile.",
                onDelete = { viewModel.deleteWatermark(watermark) }
            )
        }

        StreamTab.KEYS -> CfListScreen(
            title = "Stream",
            onBack = onBack,
            state = uiState.signingKeys,
            isRefreshing = uiState.isRefreshing,
            onRefresh = viewModel::refresh,
            emptyMessage = "No signing keys",
            key = { it.id },
            header = {
                header()
                Text(
                    // The private half exists only in the create response, so this app doesn't
                    // make that call at all.
                    "Keys that sign playback tokens for private videos. Only their ids are shown: a key's private half is returned once, when it's created, and this app doesn't create one.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp, 8.dp)
                )
            }
        ) { key ->
            DeletableListRow(
                icon = Icons.Filled.Key,
                title = key.id,
                monospaceTitle = true,
                subtitle = key.created?.let { "Created $it" },
                isDeleting = uiState.deletingId == key.id,
                deleteContentDescription = "Delete signing key",
                confirmTitle = "Delete this signing key?",
                confirmText = "Playback tokens already signed with it stop being accepted, so private videos relying on it become unplayable until they're re-signed.",
                onDelete = { viewModel.deleteSigningKey(key) }
            )
        }
    }

    uiState.liveInputForm?.let { form ->
        LiveInputSheet(form, onDismiss = viewModel::closeLiveInputForm, viewModel = viewModel)
    }
    uiState.liveInputDetail?.let { detail ->
        LiveInputDetailSheet(detail, onDismiss = viewModel::closeLiveInput)
    }
    uiState.captions?.let { captions ->
        CaptionsSheet(captions, onDismiss = viewModel::closeCaptions, viewModel = viewModel)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LiveInputSheet(
    form: LiveInputFormState,
    onDismiss: () -> Unit,
    viewModel: StreamViewModel
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Create live input", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = form.name,
                onValueChange = { v -> viewModel.updateLiveInputForm { it.copy(name = v) } },
                label = { Text("Name") },
                placeholder = { Text("Main stage") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            ToggleRow(
                title = "Record broadcasts",
                subtitle = "Keep each broadcast as a video afterwards",
                checked = form.record,
                isSaving = false,
                onToggle = { v -> viewModel.updateLiveInputForm { it.copy(record = v) } }
            )
            Text(
                "The stream key comes back once the input is created and is shown next - it's the credential a broadcaster pushes with, so treat it like a password.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (form.error != null) {
                Text(form.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            FormActions(
                isSaving = form.isSaving,
                onCancel = onDismiss,
                onSave = viewModel::saveLiveInput,
                saveLabel = "Create"
            )
        }
    }
}

/** Holds a push credential while it is open; closing the sheet is what drops it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LiveInputDetailSheet(detail: LiveInputDetail, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(liveInputName(detail.input), style = MaterialTheme.typography.titleMedium)
            when {
                detail.isLoading -> CircularProgressIndicator()
                else -> {
                    detail.error?.let { error ->
                        Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    EndpointBlock("RTMPS", detail.input.rtmps)
                    EndpointBlock("SRT", detail.input.srt)
                    EndpointBlock("WebRTC", detail.input.webRtc)
                    Text(
                        "The stream key is a credential: anyone holding it can broadcast to this input. It is shown only while this sheet is open and isn't stored on the device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun EndpointBlock(label: String, endpoint: StreamProtocolEndpoint?) {
    if (endpoint?.url == null) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        CopyableLine("URL", endpoint.url)
        endpoint.streamKey?.let { CopyableLine("Stream key", it) }
    }
}

@Composable
private fun CopyableLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
        }
        CopyIconButton(value = value, label = label)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CaptionsSheet(
    state: CaptionsState,
    onDismiss: () -> Unit,
    viewModel: StreamViewModel
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Captions", style = MaterialTheme.typography.titleMedium)
            Text(
                streamVideoTitle(state.video),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            when {
                state.isLoading -> CircularProgressIndicator()
                else -> {
                    state.error?.let { error ->
                        Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    if (state.captions.isEmpty()) {
                        Text("No caption tracks on this video", style = MaterialTheme.typography.bodyMedium)
                    }
                    state.captions.forEach { caption ->
                        DeletableListRow(
                            icon = Icons.Filled.ClosedCaption,
                            title = caption.label ?: caption.language,
                            subtitle = captionSummary(caption),
                            isDeleting = state.deletingLanguage == caption.language,
                            deleteContentDescription = "Delete caption track",
                            confirmTitle = "Delete this caption track?",
                            confirmText = "Viewers lose subtitles in ${caption.label ?: caption.language}. Uploading a replacement is done from the dashboard.",
                            onDelete = { viewModel.deleteCaption(caption) }
                        )
                    }
                    Text(
                        // A caption upload is a VTT file, which a phone has no good way to author.
                        "Adding a track means uploading a VTT file, or asking Cloudflare to generate one - both are done from the dashboard.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

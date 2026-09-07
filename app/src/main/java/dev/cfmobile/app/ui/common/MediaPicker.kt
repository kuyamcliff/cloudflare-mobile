package dev.cfmobile.app.ui.common

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import dev.cfmobile.app.data.local.contentUriPayload
import dev.cfmobile.app.data.remote.UploadPayload

/**
 * Opens Android's photo picker and hands back the chosen file ready to upload.
 *
 * The picker runs in its own process and grants access to exactly the file the user chose, so
 * this needs no storage permission and never sees the rest of the gallery. [onPicked] receives
 * null only when the file it returned can't actually be opened; a cancelled pick calls nothing.
 */
@Composable
fun rememberMediaPicker(
    type: ActivityResultContracts.PickVisualMedia.VisualMediaType,
    fallbackName: String,
    onPicked: (UploadPayload?) -> Unit
): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            onPicked(contentUriPayload(context.contentResolver, uri, fallbackName))
        }
    }
    return remember(launcher, type) {
        { launcher.launch(PickVisualMediaRequest(type)) }
    }
}

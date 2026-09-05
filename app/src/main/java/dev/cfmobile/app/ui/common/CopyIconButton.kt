package dev.cfmobile.app.ui.common

import android.content.ClipData
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch

/** PRD §42.4: copy actions always confirm, and this app only ever copies non-secret values -
 *  domain names, zone IDs, record content. Token values never go through this component (see
 *  SettingsScreen, where the token itself is never even reachable from UI state). */
@Composable
fun CopyIconButton(value: String, label: String, modifier: Modifier = Modifier) {
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    IconButton(
        onClick = {
            scope.launch {
                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(label, value)))
                Toast.makeText(context, "Copied $label", Toast.LENGTH_SHORT).show()
            }
        },
        modifier = modifier
    ) {
        Icon(Icons.Filled.ContentCopy, contentDescription = "Copy $label")
    }
}

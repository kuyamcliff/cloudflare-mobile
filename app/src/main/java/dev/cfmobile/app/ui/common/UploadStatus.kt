package dev.cfmobile.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The one line an upload needs above a list: that it's running, that it failed, or that it
 * landed. Nothing here reports progress - these uploads send the file in a single request, so
 * a progress bar would be a guess.
 */
@Composable
fun UploadStatus(
    isUploading: Boolean,
    uploadingLabel: String,
    error: String?,
    doneName: String?,
    doneSuffix: String = "uploaded"
) {
    when {
        isUploading -> Row(
            Modifier.fillMaxWidth().padding(16.dp, 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CircularProgressIndicator(Modifier.padding(2.dp))
            Text(uploadingLabel, style = MaterialTheme.typography.bodySmall)
        }
        error != null -> Text(
            error,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(16.dp, 8.dp)
        )
        doneName != null -> Text(
            "$doneName $doneSuffix",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(16.dp, 8.dp)
        )
    }
}

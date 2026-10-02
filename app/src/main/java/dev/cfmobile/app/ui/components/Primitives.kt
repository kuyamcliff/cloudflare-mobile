package dev.cfmobile.app.ui.components

import android.app.Activity
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.cfmobile.app.CfApplication

/** Secures the window (no screenshots, blank recents preview) while this is composed, for
 *  screens that show secrets (spec 191). Restores the user's global setting afterwards. */
@Composable
fun SecureWindow() {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            val app = context.applicationContext as? CfApplication
            val keepSecure = app?.container?.appLockState?.isScreenshotProtectionEnabled() == true
            if (!keepSecure) window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}

/** Copies through the app's [dev.cfmobile.app.core.security.SecureClipboard]. */
fun copyToClipboard(context: android.content.Context, label: String, value: String, sensitive: Boolean = false) {
    val app = context.applicationContext as? CfApplication ?: return
    app.container.secureClipboard.copy(label, value, sensitive)
    val message = if (sensitive) "Copied. The clipboard will be cleared shortly." else "Copied $label"
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    dev.cfmobile.app.ui.design.GroupTitle(title, modifier, action = trailing)
}

/** Label on the left, value on the right; IDs and technical values in monospace (spec 83). */
@Composable
fun KeyValueRow(
    label: String,
    value: String,
    monospace: Boolean = false,
    copyable: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = if (copyable) 2.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(120.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = if (monospace) FontFamily.Monospace else null,
            modifier = Modifier.weight(1f),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
        if (copyable) {
            IconButton(onClick = { copyToClipboard(context, label, value) }) {
                Icon(Icons.Filled.ContentCopy, contentDescription = "Copy $label", modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** A navigation row: icon, title, optional supporting text and trailing badge. */
@Composable
fun NavRow(
    title: String,
    supporting: String? = null,
    icon: ImageVector? = null,
    badge: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    dev.cfmobile.app.ui.design.ListRow(
        title = title,
        subtitle = supporting,
        icon = icon,
        enabled = enabled,
        trailing = badge?.let { b ->
            {
                b()
                if (enabled) Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
            }
        },
        onClick = onClick
    )
}

@Composable
fun ThinDivider() = HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

/** Text label plus tint; never color alone (spec 115, 242). */
@Composable
fun Badge(label: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .semantics { contentDescription = label }
    )
}

@Composable
fun MethodBadge(method: String) {
    val color = when (method) {
        "GET" -> MaterialTheme.colorScheme.tertiary
        "DELETE" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }
    Text(
        method,
        style = MaterialTheme.typography.labelSmall,
        fontFamily = FontFamily.Monospace,
        color = color,
        modifier = Modifier.width(52.dp)
    )
}

@Composable
fun InlineNotice(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Spacer(Modifier.width(0.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = color)
    }
}

package dev.cfmobile.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.cfmobile.app.ui.theme.StatusAmber
import dev.cfmobile.app.ui.theme.StatusGreen
import dev.cfmobile.app.ui.theme.StatusRed

/** The single status-pill shape used everywhere a zone/resource status, plan, or permission
 *  summary is shown (PRD §41's status/permission/plan pills) - one component instead of each
 *  screen inventing its own colored-dot-plus-text pattern. Color is never the only signal:
 *  the label text always says what state means (PRD §87, §44). */
@Composable
fun StatusPill(label: String, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = color)
    }
}

/** Maps a Cloudflare zone status string to a pill color. Unknown/new values (PRD §53) render
 *  as neutral rather than crashing or guessing a status that doesn't apply. */
@Composable
fun zoneStatusColor(status: String): Color = when (status) {
    "active" -> StatusGreen
    "pending", "initializing", "moved" -> StatusAmber
    "deactivated" -> StatusRed
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

package dev.cfmobile.app.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.cfmobile.app.ui.theme.CfTheme

/** Spacing and sizing tokens. Screens use these rather than ad hoc numbers. */
object Space {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val gutter = 16.dp
    val rowMin = 56.dp
    val tile = 36.dp
}

private val GroupRadius = 16.dp

/** Shape for item [index] of [count] in a grouped list: only the outer corners are rounded. */
fun groupShape(index: Int, count: Int): Shape {
    val top = if (index == 0) GroupRadius else 0.dp
    val bottom = if (index == count - 1) GroupRadius else 0.dp
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

/** Wraps one item of a lazily rendered group so consecutive items read as a single card. */
@Composable
fun Modifier.groupItem(index: Int, count: Int, horizontal: Dp = Space.gutter): Modifier =
    this.padding(horizontal = horizontal)
        .clip(groupShape(index, count))
        .background(CfTheme.colors.card)

/** A titled block of rows on a single card. */
@Composable
fun Group(
    modifier: Modifier = Modifier,
    title: String? = null,
    action: (@Composable () -> Unit)? = null,
    footer: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier.fillMaxWidth()) {
        if (title != null) GroupTitle(title, action = action)
        Column(
            Modifier
                .padding(horizontal = Space.gutter)
                .clip(RoundedCornerShape(GroupRadius))
                .background(CfTheme.colors.card)
                .fillMaxWidth(),
            content = content
        )
        if (footer != null) {
            Text(
                footer,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Space.gutter + 4.dp, end = Space.gutter, top = Space.sm)
            )
        }
    }
}

@Composable
fun GroupTitle(title: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(start = Space.gutter + 4.dp, end = Space.sm, top = Space.xl, bottom = Space.sm).heightIn(min = 24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f).semantics { heading() }
        )
        action?.invoke()
    }
}

/** Divider between rows inside a [Group], inset to line up with row text. */
@Composable
fun RowDivider(inset: Dp = Space.gutter) {
    HorizontalDivider(Modifier.padding(start = inset), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
}

/** A rounded square holding an icon; the only place icons get a colored ground. */
@Composable
fun IconTile(icon: ImageVector, modifier: Modifier = Modifier, tint: Color? = null, size: Dp = Space.tile) {
    val color = tint ?: MaterialTheme.colorScheme.onSurface
    Box(
        modifier.size(size).clip(RoundedCornerShape(10.dp)).background(color.copy(alpha = if (tint == null) 0.07f else 0.14f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(size * 0.55f))
    }
}

/**
 * The standard row: optional leading tile, a title, up to two supporting lines, and a trailing
 * slot. With [onClick] it shows a chevron unless [trailing] is given.
 */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    meta: String? = null,
    icon: ImageVector? = null,
    iconTint: Color? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    monospaceTitle: Boolean = false,
    enabled: Boolean = true,
    showChevron: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = Space.rowMin)
            .then(if (onClick != null) Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = Space.gutter, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.md)
    ) {
        when {
            leading != null -> leading()
            icon != null -> IconTile(icon, tint = iconTint)
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontFamily = if (monospaceTitle) FontFamily.Monospace else null,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            meta?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        trailing?.invoke(this)
        if (trailing == null && onClick != null && showChevron && enabled) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
        }
    }
}


/** Small text tag. Color is never the only signal: the label says what it means. */
@Composable
fun Tag(label: String, modifier: Modifier = Modifier, color: Color? = null) {
    val c = color ?: MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        color = c,
        maxLines = 1,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(c.copy(alpha = 0.12f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier, size: Dp = 8.dp) {
    Box(modifier.size(size).clip(CircleShape).background(color))
}

/** A selectable pill, used for context (account, zone) and filters. */
@Composable
fun Pill(
    label: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    onClick: (() -> Unit)? = null
) {
    val bg = if (selected) MaterialTheme.colorScheme.primary else CfTheme.colors.card
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Row(
        modifier
            .clip(CircleShape)
            .background(bg)
            .then(if (selected) Modifier else Modifier.border(0.5.dp, MaterialTheme.colorScheme.outline, CircleShape))
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .heightIn(min = 34.dp)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        leadingIcon?.let { Icon(it, null, tint = fg, modifier = Modifier.size(16.dp)) }
        Text(label, style = MaterialTheme.typography.labelLarge, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 200.dp))
        trailingIcon?.let { Icon(it, null, tint = fg.copy(alpha = 0.7f), modifier = Modifier.size(16.dp)) }
    }
}

/** Full-width primary action, 52dp tall. */
@Composable
fun PrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    destructive: Boolean = false,
    icon: ImageVector? = null
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(14.dp),
        colors = if (destructive) ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError
        ) else ButtonDefaults.buttonColors(),
        contentPadding = PaddingValues(horizontal = Space.lg)
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = androidx.compose.material3.LocalContentColor.current)
            Spacer(Modifier.width(Space.sm))
        } else if (icon != null) {
            Icon(icon, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Space.sm))
        }
        Text(label, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
fun SecondaryButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(14.dp)
    ) {
        if (icon != null) {
            Icon(icon, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Space.sm))
        }
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

/** Large screen title used at the top of top-level destinations. */
@Composable
fun ScreenTitle(title: String, modifier: Modifier = Modifier, subtitle: String? = null) {
    Column(modifier.fillMaxWidth().padding(horizontal = Space.gutter + 4.dp).padding(top = Space.sm, bottom = Space.xs)) {
        Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
        subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

/** Quiet, centered empty or zero state with an optional next step. */
@Composable
fun EmptyMessage(
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    icon: ImageVector? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    fill: Boolean = true
) {
    Box(
        (if (fill) modifier.fillMaxSize() else modifier.fillMaxWidth()).padding(horizontal = Space.xxl, vertical = Space.xxl),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            icon?.let { IconTile(it, size = 48.dp) }
            Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            body?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(Space.xs))
                SecondaryButton(actionLabel, onAction)
            }
        }
    }
}

/** A one-line banner for state that needs attention (token rejected, offline, expiring). */
@Composable
fun Banner(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = Space.gutter, vertical = Space.xs)
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = Space.md, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.sm)
    ) {
        StatusDot(color)
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        if (actionLabel != null && onAction != null) {
            Text(
                actionLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button, onClick = onAction).padding(horizontal = 6.dp, vertical = 4.dp)
            )
        }
    }
}

/** HTTP method label with a fixed width so paths line up. */
@Composable
fun MethodLabel(method: String, modifier: Modifier = Modifier) {
    val color = when (method.uppercase()) {
        "GET" -> MaterialTheme.colorScheme.tertiary
        "DELETE" -> MaterialTheme.colorScheme.error
        "POST" -> dev.cfmobile.app.ui.theme.StatusColors.success
        else -> dev.cfmobile.app.ui.theme.StatusColors.warning
    }
    Text(
        method.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontFamily = FontFamily.Monospace,
        color = color,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

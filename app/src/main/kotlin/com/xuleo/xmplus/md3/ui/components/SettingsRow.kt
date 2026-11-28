package com.xuleo.xmplus.md3.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xuleo.xmplus.md3.core.designsystem.XmLayout
import com.xuleo.xmplus.md3.ui.icons.XmIcons

/** The smallest a settings row may be; it grows for a second line of text. */
private val RowMinHeight = 56.dp

/** Space above and below a row's text, which keeps a two-line row near 64dp instead of ListItem's 72dp. */
private val RowVerticalPadding = 8.dp

/** Space between a row's icon and its text, and between the text and what follows it. */
private val RowGap = 16.dp

/**
 * The card a settings page groups its rows in: one tonal surface per group, so the rows inside sit next to each other
 * rather than each floating in a card of its own.
 */
@Composable
internal fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        Modifier.padding(horizontal = XmLayout.PagePadding).fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(vertical = 4.dp), content = content)
    }
}

/** The line between two rows of a [SettingsCard], starting where their text does. */
@Composable
internal fun SettingsDivider() {
    HorizontalDivider(
        Modifier.padding(start = XmLayout.PagePadding + 24.dp + RowGap),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    )
}

/**
 * One line of a [SettingsCard]: an icon, a name, an optional line under it and something at the end. Tapping gives no
 * ripple, as the rest of the app does; the row is its own touch target, so callers need no extra padding.
 */
@Composable
internal fun SettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    supporting: String? = null,
    enabled: Boolean = true,
    role: Role? = Role.Button,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier.clickable(
                    interactionSource = interaction, indication = null, enabled = enabled, role = role, onClick = onClick,
                ) else Modifier,
            )
            .heightIn(min = RowMinHeight)
            .padding(horizontal = XmLayout.PagePadding, vertical = RowVerticalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RowGap),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (supporting != null) {
                Text(
                    supporting, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // A row that opens something says so; one that holds a switch brings its own ending.
        if (trailing != null) trailing() else if (onClick != null) Icon(
            XmIcons.Outlined.ChevronRight, contentDescription = null, modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

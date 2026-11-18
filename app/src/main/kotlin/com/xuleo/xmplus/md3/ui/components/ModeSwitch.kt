package com.xuleo.xmplus.md3.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xuleo.xmplus.md3.core.designsystem.XmLayout
import com.xuleo.xmplus.md3.core.designsystem.XmPreview
import com.xuleo.xmplus.md3.core.designsystem.XmPreviews

/**
 * The modes as a row of headings: the chosen one is bold and in the accent colour, the others are quiet. There is
 * no track or outline, so it reads as a page heading above the channel tabs.
 */
@Composable
fun <T> ModeSwitch(items: List<T>, selected: T, label: @Composable (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.padding(horizontal = XmLayout.PagePadding), horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
        items.forEach { item ->
            val active = item == selected
            val color by animateColorAsState(
                if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                MaterialTheme.motionScheme.defaultEffectsSpec(), label = "modeColor",
            )
            Box(
                Modifier.heightIn(min = 48.dp).selectable(
                    selected = active, interactionSource = remember { MutableInteractionSource() }, indication = null,
                    role = Role.Tab, onClick = { onSelect(item) },
                ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label(item), color = color, style = MaterialTheme.typography.titleLarge,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@XmPreviews
@Composable
private fun ModeSwitchPreview() = XmPreview { ModeSwitch(listOf("For you", "Categories", "Charts"), "Categories", { it }, {}) }
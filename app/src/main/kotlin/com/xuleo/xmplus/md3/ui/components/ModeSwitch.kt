package com.xuleo.xmplus.md3.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xuleo.xmplus.md3.core.designsystem.XmPreview
import com.xuleo.xmplus.md3.core.designsystem.XmPreviews

/**
 * A switch between a few modes: a soft track with one pill lit in the accent colour. It is not an outline or a
 * tab row, so it reads as a different level from the channel tabs under it.
 */
@Composable
fun <T> ModeSwitch(items: List<T>, selected: T, label: @Composable (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items.forEach { item ->
            val active = item == selected
            val container by animateColorAsState(
                if (active) MaterialTheme.colorScheme.primary else Color.Transparent,
                MaterialTheme.motionScheme.defaultEffectsSpec(), label = "modeContainer",
            )
            val content by animateColorAsState(
                if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                MaterialTheme.motionScheme.defaultEffectsSpec(), label = "modeContent",
            )
            Box(
                Modifier.weight(1f).heightIn(min = 40.dp).clip(CircleShape).background(container)
                    .selectable(selected = active, role = Role.Tab, onClick = { onSelect(item) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label(item), Modifier.padding(horizontal = 8.dp), color = content,
                    style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@XmPreviews
@Composable
private fun ModeSwitchPreview() = XmPreview { ModeSwitch(listOf("For you", "Categories", "Charts"), "Categories", { it }, {}) }
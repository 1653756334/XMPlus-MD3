package com.xuleo.xmplus.md3.ui.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.SelectableDropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import com.xuleo.xmplus.md3.ui.icons.XmIcons

/** The app's menu: the expressive popup with its items in one rounded group, scrolling when it outgrows the screen. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun XmMenu(expanded: Boolean, onDismissRequest: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    DropdownMenuPopup(expanded, onDismissRequest) {
        DropdownMenuGroup(
            shapes = MenuDefaults.groupShape(0, 1),
            modifier = Modifier.verticalScroll(rememberScrollState()),
            content = content,
        )
    }
}

/** An action in an [XmMenu]; [index] of [count] rounds the first and last items to the group. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun XmMenuItem(
    text: String,
    onClick: () -> Unit,
    index: Int,
    count: Int,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    DropdownMenuItem(
        onClick = onClick,
        text = { Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        shape = MenuDefaults.itemShape(index, count).shape,
        leadingIcon = icon?.let { { Icon(it, contentDescription = null, Modifier.size(MenuDefaults.LeadingIconSize)) } },
        enabled = enabled,
    )
}

/** One of the choices in an [XmMenu]: the chosen one is filled, with a check after its name. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun XmMenuChoice(text: String, selected: Boolean, onClick: () -> Unit, index: Int, count: Int) {
    SelectableDropdownMenuItem(
        selected = selected,
        onClick = onClick,
        text = { Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        shapes = MenuDefaults.itemShape(index, count),
        trailingContent = if (selected) {
            { Icon(XmIcons.Outlined.Check, contentDescription = null, Modifier.size(MenuDefaults.TrailingIconSize)) }
        } else null,
    )
}

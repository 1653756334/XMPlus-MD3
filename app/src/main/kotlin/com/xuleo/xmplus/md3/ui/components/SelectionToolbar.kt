@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.xuleo.xmplus.md3.ui.components

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/** Room a list leaves at its end while [SelectionToolbar] shows, so the last row stays above it. */
internal val SelectionToolbarSpace = 112.dp

/**
 * The actions on what is selected, floating at the bottom of the page in reach of the thumb. The top bar keeps only
 * ✕, the count and 全选.
 */
@Composable
internal fun BoxScope.SelectionToolbar(visible: Boolean, modifier: Modifier = Modifier, actions: @Composable RowScope.() -> Unit) {
    val motion = MaterialTheme.motionScheme
    AnimatedVisibility(
        visible = visible,
        modifier = modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = FloatingToolbarDefaults.ScreenOffset),
        enter = slideInVertically(motion.defaultSpatialSpec()) { it } + fadeIn(motion.defaultEffectsSpec()),
        exit = slideOutVertically(motion.fastSpatialSpec()) { it } + fadeOut(motion.fastEffectsSpec()),
    ) {
        HorizontalFloatingToolbar(expanded = true, colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(), content = actions)
    }
}

/** One labelled action of a floating toolbar. */
@Composable
internal fun ToolbarAction(@StringRes label: Int, icon: ImageVector, enabled: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.widthIn(min = 64.dp).clip(MaterialTheme.shapes.large)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .alpha(if (enabled) 1f else 0.38f)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(icon, contentDescription = null)
        Text(stringResource(label), style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

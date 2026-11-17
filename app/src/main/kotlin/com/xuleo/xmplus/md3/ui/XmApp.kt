package com.xuleo.xmplus.md3.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ShortNavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteItem
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.xuleo.xmplus.md3.core.designsystem.XmDevicePreviews
import com.xuleo.xmplus.md3.core.designsystem.XmPreview
import com.xuleo.xmplus.md3.core.designsystem.XmPreviews
import com.xuleo.xmplus.md3.ui.screens.DiscoverScreen
import com.xuleo.xmplus.md3.ui.screens.DownloadsScreen
import com.xuleo.xmplus.md3.ui.screens.LibraryScreen
import com.xuleo.xmplus.md3.ui.screens.MeScreen

@Composable
fun XmApp() {
    var selected by rememberSaveable { mutableStateOf(Destination.Discover) }

    // Back from any other tab returns to Discover; back on Discover leaves the app.
    BackHandler(enabled = selected != Destination.Discover) {
        selected = Destination.Discover
    }

    XmAppContent(selected = selected, onSelect = { selected = it })
}

@Composable
internal fun XmAppContent(selected: Destination, onSelect: (Destination) -> Unit) {
    val stateHolder = rememberSaveableStateHolder()
    val navigationType = NavigationSuiteScaffoldDefaults.navigationSuiteType(currentWindowAdaptiveInfoV2())
    // Icon over label with no indicator pill: the selected tab only turns primary and gets the filled icon.
    val itemColors = ShortNavigationBarItemDefaults.colors(
        selectedIconColor = MaterialTheme.colorScheme.primary,
        selectedTextColorTopIconPosition = MaterialTheme.colorScheme.primary,
        selectedTextColorStartIconPosition = MaterialTheme.colorScheme.primary,
        selectedIndicatorColor = Color.Transparent,
        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    NavigationSuiteScaffold(
        navigationSuiteType = navigationType,
        navigationSuiteColors = NavigationSuiteDefaults.colors(
            shortNavigationBarContainerColor = MaterialTheme.colorScheme.surface,
        ),
        navigationItems = {
            Destination.entries.forEach { destination ->
                val isSelected = destination == selected
                NavigationSuiteItem(
                    selected = isSelected,
                    onClick = { onSelect(destination) },
                    icon = {
                        Icon(
                            imageVector = if (isSelected) destination.selectedIcon else destination.icon,
                            contentDescription = null,
                        )
                    },
                    label = { Text(stringResource(destination.label)) },
                    colors = itemColors,
                    navigationSuiteType = navigationType,
                )
            }
        },
    ) {
        Column(Modifier.fillMaxSize()) {
            Crossfade(
                targetState = selected,
                modifier = Modifier.weight(1f),
                animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                label = "tab",
            ) { destination ->
                TabContent(destination, stateHolder)
            }
            MiniPlayer()
        }
    }
}

// Each tab keeps its own saved state (scroll position, inputs) while it is off screen.
@Composable
private fun TabContent(destination: Destination, stateHolder: SaveableStateHolder) {
    stateHolder.SaveableStateProvider(destination.name) {
        when (destination) {
            Destination.Discover -> DiscoverScreen()
            Destination.Library -> LibraryScreen()
            Destination.Downloads -> DownloadsScreen()
            Destination.Me -> MeScreen()
        }
    }
}

@XmDevicePreviews
@Composable
private fun XmAppPreview() = XmPreview { XmAppContent(selected = Destination.Discover, onSelect = {}) }

@XmPreviews
@Composable
private fun XmAppMePreview() = XmPreview { XmAppContent(selected = Destination.Me, onSelect = {}) }

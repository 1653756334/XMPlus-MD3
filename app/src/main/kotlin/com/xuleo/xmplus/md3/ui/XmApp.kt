package com.xuleo.xmplus.md3.ui

import androidx.activity.BackEventCompat
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.navigationsuite.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.*
import androidx.navigation3.ui.NavDisplay
import com.xuleo.xmplus.md3.XmApplication
import com.xuleo.xmplus.md3.account.AccountViewModel
import com.xuleo.xmplus.md3.core.account.AccountsState
import com.xuleo.xmplus.md3.core.designsystem.*
import com.xuleo.xmplus.md3.ui.screens.*

@Composable
fun XmApp() {
    val application = LocalContext.current.applicationContext as XmApplication
    val accounts: AccountViewModel = viewModel(factory = AccountViewModel.factory(application.accounts, application.login))
    var selected by rememberSaveable { mutableStateOf(Destination.Discover) }
    XmAppContent(selected, { selected = it }, accounts)
}

@Composable
internal fun XmAppContent(selected: Destination, onSelect: (Destination) -> Unit, accounts: AccountViewModel? = null) {
    val stack = rememberNavBackStack(AppRoute.Home)
    val navigator = remember(stack, accounts) {
        AccountNavigator(stack) { removed ->
            when (removed) {
                is AppRoute.ImportLogin -> accounts?.closeImport()
                AppRoute.PhoneLogin -> accounts?.closePhone()
                else -> accounts?.clearError()
            }
        }
    }
    val isHome = stack.lastOrNull() == AppRoute.Home
    val stateHolder = rememberSaveableStateHolder()
    val security = rememberSensitiveWindow()
    val suite = rememberNavigationSuiteScaffoldState()
    val navigationType = NavigationSuiteScaffoldDefaults.navigationSuiteType(currentWindowAdaptiveInfoV2())
    val accountState = accounts?.accounts?.collectAsStateWithLifecycle()?.value ?: AccountsState.Ready(emptyList())

    BackHandler(enabled = isHome && selected != Destination.Discover) { onSelect(Destination.Discover) }
    LaunchedEffect(isHome) { if (isHome) suite.show() else suite.hide() }
    LaunchedEffect(accounts?.importCompleted) {
        if (accounts?.importCompleted == true) {
            navigator.authenticated()
            accounts.consumeImport()
        }
    }
    fun openPhone() {
        accounts?.clearError()
        navigator.push(AppRoute.PhoneLogin)
    }
    fun openImport(identity: String? = null) {
        if (stack.lastOrNull() !is AppRoute.ImportLogin) accounts?.beginImport()
        navigator.push(AppRoute.ImportLogin(identity))
    }

    val colors = ShortNavigationBarItemDefaults.colors(
        selectedIconColor = MaterialTheme.colorScheme.primary,
        selectedTextColorTopIconPosition = MaterialTheme.colorScheme.primary,
        selectedTextColorStartIconPosition = MaterialTheme.colorScheme.primary,
        selectedIndicatorColor = Color.Transparent,
        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val direction = if (LocalLayoutDirection.current == LayoutDirection.Ltr) 1 else -1
    NavigationSuiteScaffold(
        state = suite,
        navigationSuiteType = navigationType,
        navigationSuiteColors = NavigationSuiteDefaults.colors(shortNavigationBarContainerColor = MaterialTheme.colorScheme.surface),
        navigationItems = {
            CompositionLocalProvider(LocalRippleConfiguration provides null) {
                Destination.entries.forEach { destination ->
                    val active = destination == selected
                    NavigationSuiteItem(
                        selected = active, onClick = { onSelect(destination) },
                        icon = { Icon(if (active) destination.selectedIcon else destination.icon, contentDescription = null) },
                        label = { Text(stringResource(destination.label)) },
                        colors = colors, navigationSuiteType = navigationType,
                    )
                }
            }
        },
    ) {
        // One player, outside every entry transition. IME insets move it above the keyboard.
        Column(Modifier.fillMaxSize().imePadding()) {
            NavDisplay(
                backStack = stack,
                onBack = { navigator.pop() },
                modifier = Modifier.weight(1f).clipToBounds(),
                entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()),
                transitionSpec = {
                    (slideInHorizontally(spatial) { it * direction } + fadeIn(effects)) togetherWith
                        (slideOutHorizontally(spatial) { -it * direction / 3 } + fadeOut(effects))
                },
                popTransitionSpec = {
                    (slideInHorizontally(spatial) { -it * direction / 3 } + fadeIn(effects)) togetherWith
                        (slideOutHorizontally(spatial) { it * direction } + fadeOut(effects))
                },
                predictivePopTransitionSpec = { edge ->
                    val sign = if (edge == BackEventCompat.EDGE_RIGHT) -1 else 1
                    (slideInHorizontally(spatial) { -it * sign / 3 } + fadeIn(effects)) togetherWith
                        (slideOutHorizontally(spatial) { it * sign } + fadeOut(effects))
                },
                entryProvider = entryProvider {
                    entry<AppRoute.Home> {
                        Surface(Modifier.fillMaxSize()) {
                            Crossfade(selected, animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(), label = "tab") { tab ->
                                TabContent(tab, stateHolder, accounts,
                                    { navigator.push(AppRoute.Accounts) }, ::openPhone)
                            }
                        }
                    }
                    entry<AppRoute.Accounts> {
                        Surface(Modifier.fillMaxSize()) {
                            AccountsContent(accountState, accounts?.busy == true, accounts?.errorCode,
                                { navigator.pop() }, ::openPhone, { accounts?.setPrimary(it) }, { accounts?.remove(it) },
                                { accounts?.refreshQuota(it) }, { accounts?.retryLoad() }, { openImport(it) })
                        }
                    }
                    entry<AppRoute.PhoneLogin> {
                        SensitiveEntry(security) {
                            Surface(Modifier.fillMaxSize()) {
                                PhoneLoginScreen(accounts, { navigator.pop() }, { openImport() })
                            }
                        }
                    }
                    entry<AppRoute.ImportLogin> { route ->
                        SensitiveEntry(security) {
                            Surface(Modifier.fillMaxSize()) {
                                var primary by rememberSaveable { mutableStateOf(true) }
                                ImportAccountContent(accounts?.draft.orEmpty(), primary, accounts?.busy == true,
                                    accounts?.errorCode, { accounts?.updateDraft(it) }, { primary = it },
                                    { accounts?.importCredentials(primary, route.recoveryIdentity) }, { navigator.pop() },
                                    recovery = route.recoveryIdentity != null, onPhoneLogin = ::openPhone)
                            }
                        }
                    }
                },
            )
            MiniPlayer()
        }
    }
}

@Composable
private fun TabContent(destination: Destination, holder: SaveableStateHolder, accounts: AccountViewModel?,
    onAccounts: () -> Unit, onLogin: () -> Unit) {
    holder.SaveableStateProvider(destination.name) {
        when (destination) {
            Destination.Discover -> DiscoverScreen()
            Destination.Library -> LibraryScreen()
            Destination.Downloads -> DownloadsScreen()
            Destination.Me -> MeScreen(accounts, onAccounts, onLogin)
        }
    }
}

@XmDevicePreviews
@Composable
private fun XmAppPreview() = XmPreview { XmAppContent(Destination.Discover, {}) }

@XmPreviews
@Composable
private fun XmAppMePreview() = XmPreview { XmAppContent(Destination.Me, {}) }

package com.xuleo.xmplus.md3.ui

import com.xuleo.xmplus.md3.core.media.MusicPlaySource
import com.xuleo.xmplus.md3.core.model.MusicItem
import com.xuleo.xmplus.md3.core.media.SongResume
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
import com.xuleo.xmplus.md3.browse.AlbumViewModel
import com.xuleo.xmplus.md3.browse.AllAlbumsViewModel
import com.xuleo.xmplus.md3.browse.AnchorViewModel
import com.xuleo.xmplus.md3.browse.CategoriesViewModel
import com.xuleo.xmplus.md3.browse.CategoryAlbumsViewModel
import com.xuleo.xmplus.md3.browse.DiscoverViewModel
import com.xuleo.xmplus.md3.browse.RankingsViewModel
import com.xuleo.xmplus.md3.browse.RouteStores
import com.xuleo.xmplus.md3.browse.SearchViewModel
import com.xuleo.xmplus.md3.core.account.AccountsState
import com.xuleo.xmplus.md3.core.designsystem.*
import com.xuleo.xmplus.md3.core.media.PlaybackController
import com.xuleo.xmplus.md3.core.media.HistoryUploader
import com.xuleo.xmplus.md3.core.model.CategoryChannel
import com.xuleo.xmplus.md3.core.model.CategoryGroup
import com.xuleo.xmplus.md3.core.model.HotWord
import com.xuleo.xmplus.md3.core.protocol.XmlyTranscript
import com.xuleo.xmplus.md3.library.CommentActions
import com.xuleo.xmplus.md3.library.LibraryActions
import com.xuleo.xmplus.md3.download.DownloadActions
import com.xuleo.xmplus.md3.download.DownloadsViewModel
import com.xuleo.xmplus.md3.library.LibraryViewModel
import com.xuleo.xmplus.md3.music.MusicBrowseViewModel
import com.xuleo.xmplus.md3.music.MusicDetailKind
import com.xuleo.xmplus.md3.music.MusicSearchViewModel
import com.xuleo.xmplus.md3.core.model.MusicAlbum
import com.xuleo.xmplus.md3.core.model.MusicArtist
import com.xuleo.xmplus.md3.core.model.MusicSheet
import com.xuleo.xmplus.md3.music.MusicDetailViewModel
import com.xuleo.xmplus.md3.music.musicDetailItem
import com.xuleo.xmplus.md3.music.routeRaw
import com.xuleo.xmplus.md3.core.model.PluginMedia
import com.xuleo.xmplus.md3.music.PluginsViewModel
import com.xuleo.xmplus.md3.ui.components.ImportSheetSheet
import com.xuleo.xmplus.md3.ui.screens.*

@Composable
fun XmApp() {
    val application = LocalContext.current.applicationContext as XmApplication
    val accounts: AccountViewModel = viewModel(
        factory = AccountViewModel.factory(application.accounts, application.login, application.playback, application.profile),
    )
    val discover: DiscoverViewModel = viewModel(factory = DiscoverViewModel.factory(application.home, application.browse, application.accounts))
    val search: SearchViewModel = viewModel(factory = SearchViewModel.factory(application.search, application.discovery, application.searchHistory))
    val allAlbums: AllAlbumsViewModel = viewModel(factory = AllAlbumsViewModel.factory(application.browse))
    val categories: CategoriesViewModel = viewModel(factory = CategoriesViewModel.factory(application.discovery))
    val categoryAlbums: CategoryAlbumsViewModel = viewModel(factory = CategoryAlbumsViewModel.factory(application.discovery))
    val rankings: RankingsViewModel = viewModel(factory = RankingsViewModel.factory(application.discovery))
    val library: LibraryViewModel = viewModel(factory = LibraryViewModel.factory(application))
    val downloads: DownloadsViewModel = viewModel(factory = DownloadsViewModel.factory(application))
    val plugins: PluginsViewModel = viewModel(factory = PluginsViewModel.factory(application))
    val musicBrowse: MusicBrowseViewModel = viewModel(factory = MusicBrowseViewModel.factory(application))
    val musicSearch: MusicSearchViewModel = viewModel(factory = MusicSearchViewModel.factory(application))
    var selected by rememberSaveable { mutableStateOf(Destination.Discover) }
    // Album and anchor pages live as long as their route is on the stack.
    val routeStores: RouteStores = viewModel()
    val albumPage: @Composable (AppRoute.AlbumPage) -> AlbumViewModel = { route ->
        viewModel(viewModelStoreOwner = routeStores.owner(route.contentKey), factory = AlbumViewModel.factory(route.albumId, application.album, application.accounts, application.libraryActions, application.downloadActions))
    }
    val anchorPage: @Composable (AppRoute.AnchorPage) -> AnchorViewModel = { route ->
        viewModel(viewModelStoreOwner = routeStores.owner(route.contentKey), factory = AnchorViewModel.factory(route, application.anchor, application.accounts, application.libraryActions))
    }
    val musicPage: @Composable (AppRoute.MusicDetail) -> MusicDetailViewModel? = { route ->
        val kind = MusicDetailKind.entries.firstOrNull { it.name == route.kind }
        val item = kind?.let { musicDetailItem(it, route.platform, route.raw) }
        if (kind == null || item == null) null
        else viewModel(viewModelStoreOwner = routeStores.owner(route.contentKey), factory = MusicDetailViewModel.factory(application.musicCatalog, kind, item))
    }
    // 下载 in every song menu and on the player reads the same tasks and the same download quality setting.
    val songDownloads = remember(application) {
        com.xuleo.xmplus.md3.ui.components.SongDownloads(
            application.downloadActions.tasks, application.musicQuality.download,
            application.downloadActions::downloadSong, application.downloadActions::pause, application.downloadActions::resume,
        )
    }
    val appSnackbar = remember { SnackbarHostState() }
    val appScope = rememberCoroutineScope()
    CompositionLocalProvider(
        com.xuleo.xmplus.md3.ui.components.LocalAppSnackbar provides appSnackbar,
        com.xuleo.xmplus.md3.ui.components.LocalAppSnackbarScope provides appScope,
        com.xuleo.xmplus.md3.ui.components.LocalMusicLibrary provides application.musicLibrary,
        com.xuleo.xmplus.md3.ui.components.LocalSongDownloads provides songDownloads,
    ) { XmAppContent(
        selected, { selected = it }, accounts, discover, search, allAlbums, categories, categoryAlbums, rankings, albumPage,
        anchorPage = anchorPage, onRoutePopped = routeStores::remove, playback = application.playback, library = library, downloads = downloads, downloadActions = application.downloadActions,
        transcript = application.transcript, libraryActions = application.libraryActions, comments = application.commentActions,
        plugins = plugins, musicBrowse = musicBrowse, musicPage = musicPage, musicSearch = musicSearch,
        musicPlayer = remember(application) {
            MusicPlayerDeps(application.music, application.musicLyrics, application.musicCatalog, application.musicLibrary)
        },
    ) }
}

@Composable
internal fun XmAppContent(
    selected: Destination,
    onSelect: (Destination) -> Unit,
    accounts: AccountViewModel? = null,
    discover: DiscoverViewModel? = null,
    search: SearchViewModel? = null,
    allAlbums: AllAlbumsViewModel? = null,
    categories: CategoriesViewModel? = null,
    categoryAlbums: CategoryAlbumsViewModel? = null,
    rankings: RankingsViewModel? = null,
    albumPage: @Composable (AppRoute.AlbumPage) -> AlbumViewModel? = { null },
    anchorPage: @Composable (AppRoute.AnchorPage) -> AnchorViewModel? = { null },
    onRoutePopped: (Any) -> Unit = {},
    playback: PlaybackController? = null,
    library: LibraryViewModel? = null,
    transcript: XmlyTranscript? = null,
    libraryActions: LibraryActions? = null,
    comments: CommentActions? = null,
    downloads: DownloadsViewModel? = null,
    downloadActions: DownloadActions? = null,
    plugins: PluginsViewModel? = null,
    musicBrowse: MusicBrowseViewModel? = null,
    musicPage: @Composable (AppRoute.MusicDetail) -> MusicDetailViewModel? = { null },
    musicSearch: MusicSearchViewModel? = null,
    musicPlayer: MusicPlayerDeps? = null,
) {
    val stack = rememberNavBackStack(AppRoute.Home)
    val navigator = remember(stack, accounts) {
        AccountNavigator(stack) { removed ->
            when (removed) {
                is AppRoute.ImportLogin -> accounts?.closeImport()
                AppRoute.PhoneLogin -> accounts?.closePhone()
                AppRoute.Search, is AppRoute.AllAlbums, is AppRoute.CategoryAlbums, is AppRoute.HotRanking,
                is AppRoute.AlbumPage, is AppRoute.AnchorPage, AppRoute.Player, AppRoute.Plugins, is AppRoute.MusicDetail, AppRoute.MusicSearch,
                AppRoute.Recent, is AppRoute.LibraryPage, is AppRoute.LocalSheet, AppRoute.LibrarySearch -> Unit
                else -> accounts?.clearError()
            }
        }
    }
    // Keep the underlying page and mini player in place while the player slides over them.
    val pages = stack.filterNot { it == AppRoute.Player }
    val isHome = pages.lastOrNull() == AppRoute.Home
    val isPlayer = stack.lastOrNull() == AppRoute.Player
    val stateHolder = rememberSaveableStateHolder()
    val security = rememberSensitiveWindow()
    val suite = rememberNavigationSuiteScaffoldState()
    val navigationType = NavigationSuiteScaffoldDefaults.navigationSuiteType(currentWindowAdaptiveInfoV2())
    val accountState = accounts?.accounts?.collectAsStateWithLifecycle()?.value ?: AccountsState.Ready(emptyList())
    val hotWords = search?.state?.collectAsStateWithLifecycle()?.value?.hotWords.orEmpty()
    val playingSong = musicPlayer?.playback?.state?.collectAsStateWithLifecycle()?.value?.song
    val playingTrack = playback?.state?.collectAsStateWithLifecycle()?.value?.selection?.track?.id
    val nowPlaying = if (playingSong != null) NowPlaying(songKey = playingSong.key) else NowPlaying(trackId = playingTrack)
    val installedPlugins = plugins?.plugins?.collectAsStateWithLifecycle()?.value.orEmpty()

    BackHandler(enabled = !isPlayer && isHome && selected != Destination.Discover) { onSelect(Destination.Discover) }
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
    fun openSearch() {
        search?.clear()
        navigator.push(AppRoute.Search)
    }
    fun openAllAlbums(categoryId: Int) {
        allAlbums?.start(categoryId)
        navigator.push(AppRoute.AllAlbums(categoryId))
    }
    fun openCategory(group: CategoryGroup, channel: CategoryChannel?) {
        categoryAlbums?.start(group.id, channel?.relationMetadataValueId)
        navigator.push(AppRoute.CategoryAlbums(group.id, group.title, channel?.relationMetadataValueId, channel?.title))
    }
    fun openAlbum(albumId: String) {
        navigator.push(AppRoute.AlbumPage(albumId))
    }
    fun openAnchor(uid: String) {
        navigator.push(AppRoute.AnchorPage(uid))
    }
    fun openMusic(kind: MusicDetailKind, item: PluginMedia) {
        navigator.push(AppRoute.MusicDetail(kind.name, item.platform, item.id, item.routeRaw()))
    }
    fun openMusic(item: PluginMedia) = when (item) {
        is MusicSheet -> openMusic(MusicDetailKind.Sheet, item)
        is MusicAlbum -> openMusic(MusicDetailKind.Album, item)
        is MusicArtist -> openMusic(MusicDetailKind.Artist, item)
        else -> Unit
    }
    fun openAlbumAnchor(albumId: String, uid: String?) {
        if (uid != null) openAnchor(uid)
        else navigator.push(AppRoute.AnchorPage(albumId, resolveFromAlbum = true))
    }
    fun openPlayer() {
        if (stack.lastOrNull() == AppRoute.Player) return
        // An older saved stack may have left the player under an account page.
        stack.remove(AppRoute.Player)
        navigator.push(AppRoute.Player)
    }
    val links = DiscoverLinks(::openSearch, ::openAllAlbums, { navigator.push(AppRoute.HotRanking(it)) }, ::openCategory, ::openAlbum, ::openAnchor)
    fun playSaved(albumId: String, trackId: String, positionMs: Long) {
        playback?.playSaved(albumId, trackId, positionMs)
    }
    // 继续听 and 最近播放 bring a song back with the list it was played from, where it stopped.
    fun playSong(resume: SongResume) {
        musicPlayer?.playback?.playList(resume.songs, resume.index, source = resume.source, positionMs = resume.positionMs)
    }
    // 从链接导入 opens over the 听过 page; the sheet it creates opens on top of it.
    var importing by remember { mutableStateOf(false) }
    val libraryLinks = LibraryLinks(
        onOpenAlbum = ::openAlbum,
        onPlay = ::playSaved,
        onPlaySong = ::playSong,
        onOpenRecent = { navigator.push(AppRoute.Recent) },
        onOpenSection = { navigator.push(AppRoute.LibraryPage(it.name)) },
        onOpenStarred = { openMusic(MusicDetailKind.Sheet, it) },
        // Hooks for the next steps: library search and 导入.
        onOpenSheet = { navigator.push(AppRoute.LocalSheet(it)) },
        onSearch = { navigator.push(AppRoute.LibrarySearch) },
        onImportSheet = { importing = true },
        onPlayLikes = { songs -> musicPlayer?.playback?.playList(songs, 0, source = MusicPlaySource.Likes) },
        onLogin = ::openPhone,
    )
    val libraryTab: @Composable () -> Unit = {
        if (library != null) {
            LibraryScreen(library, musicPlayer?.library, accountState.hasAccounts, nowPlaying, libraryLinks)
        }
    }
    fun openImport(identity: String? = null) {
        if (stack.lastOrNull() !is AppRoute.ImportLogin) accounts?.beginImport()
        navigator.push(AppRoute.ImportLogin(identity))
    }

    val importArchive = rememberArchiveImport { read -> accounts?.previewArchive(read) }
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
    val routeDecorator = remember(onRoutePopped) { NavEntryDecorator<NavKey>(onPop = onRoutePopped) { it.Content() } }
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
        // One player on every page, outside every entry transition. IME insets move it above the keyboard.
        Column(Modifier.fillMaxSize().imePadding()) {
            Box(Modifier.weight(1f)) {
            NavDisplay(
                backStack = pages,
                onBack = { if (!isPlayer) navigator.pop() },
                modifier = Modifier.fillMaxSize().clipToBounds(),
                entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(), routeDecorator),
                transitionSpec = { openTransform(direction, spatial, effects) },
                // Back, by gesture from either edge or by button, is the opening slide in reverse (see BackMotion.kt).
                popTransitionSpec = { backTransform(direction, spatial, effects) },
                predictivePopTransitionSpec = { backTransform(direction, spatial, effects) },
                entryProvider = entryProvider {
                    entry<AppRoute.Home> {
                        Surface(Modifier.fillMaxSize()) {
                            Crossfade(selected, animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(), label = "tab") { tab ->
                                TabContent(tab, stateHolder, accounts, discover, categories, rankings, hotWords, links,
                                    { navigator.push(AppRoute.Accounts) }, ::openPhone, importArchive, playback?.historyUpload, library, downloads, ::openAlbum, ::openAnchor, { albumId, trackId, positionMs -> playback?.playSaved(albumId, trackId, positionMs) },
                                    { albumId, trackId -> playback?.playEpisode(albumId, trackId) },
                                    { songs -> musicPlayer?.playback?.playList(songs, 0, source = MusicPlaySource(MusicPlaySource.Type.Download)) }, plugins, { navigator.push(AppRoute.Plugins) }, musicBrowse,
                                    { sheet, ranking -> openMusic(if (ranking) MusicDetailKind.TopList else MusicDetailKind.Sheet, sheet) },
                                    { navigator.push(AppRoute.MusicSearch) }, libraryTab = libraryTab)
                            }
                        }
                    }
                    entry<AppRoute.Search> {
                        Surface(Modifier.fillMaxSize()) { if (search != null) SearchScreen(search, ::openAlbum, ::openAnchor) { navigator.pop() } }
                    }
                    entry<AppRoute.AllAlbums> { route ->
                        Surface(Modifier.fillMaxSize()) { if (allAlbums != null) AllAlbumsScreen(allAlbums, route.categoryId, ::openAlbum) { navigator.pop() } }
                    }
                    entry<AppRoute.CategoryAlbums> { route ->
                        Surface(Modifier.fillMaxSize()) {
                            if (categoryAlbums != null) {
                                CategoryAlbumsScreen(categoryAlbums, route.groupId, route.groupTitle, route.channelId, route.channelTitle, ::openAlbum) { navigator.pop() }
                            }
                        }
                    }
                    entry<AppRoute.HotRanking> { route ->
                        Surface(Modifier.fillMaxSize()) { if (discover != null) HotRankingScreen(discover, route.pageId, ::openAlbum) { navigator.pop() } }
                    }
                    entry<AppRoute.AlbumPage>(clazzContentKey = { it.contentKey }) { route ->
                        Surface(Modifier.fillMaxSize()) {
                            albumPage(route)?.let { vm ->
                                AlbumScreen(
                                    vm, { navigator.pop() }, ::openAlbum,
                                    onOpenAnchor = { uid -> openAlbumAnchor(route.albumId, uid) },
                                    onPlayEpisode = { album, track -> playback?.playAlbum(album, track) },
                                )
                            }
                        }
                    }
                    entry<AppRoute.AnchorPage>(clazzContentKey = { it.contentKey }) { route ->
                        Surface(Modifier.fillMaxSize()) {
                            anchorPage(route)?.let { AnchorScreen(it, ::openAlbum) { navigator.pop() } }
                        }
                    }
                    entry<AppRoute.MusicSearch> {
                        Surface(Modifier.fillMaxSize()) {
                            if (musicSearch != null) {
                                MusicSearchScreen(musicSearch, plugins?.plugins?.collectAsStateWithLifecycle()?.value.orEmpty(), { navigator.pop() },
                                    onOpen = ::openMusic, onPlay = { musicPlayer?.playback?.playNow(it, MusicPlaySource(MusicPlaySource.Type.Search)) },
                                    onPlayNext = { musicPlayer?.playback?.addNext(it) }, playingKey = playingSong?.key,
                                    source = plugins?.source?.collectAsStateWithLifecycle()?.value, onSource = { plugins?.selectSource(it) })
                            }
                        }
                    }
                    entry<AppRoute.MusicDetail>(clazzContentKey = { it.contentKey }) { route ->
                        Surface(Modifier.fillMaxSize()) {
                            musicPage(route)?.let { vm ->
                                MusicDetailScreen(
                                    vm, { navigator.pop() },
                                    onOpenAlbum = { openMusic(MusicDetailKind.Album, it) },
                                    onPlay = { songs, index -> musicPlayer?.playback?.playList(songs, index, vm.queuePages(), vm.playSource()) },
                                    onPlayNext = { musicPlayer?.playback?.addNext(it) },
                                    playingKey = playingSong?.key,
                                )
                            }
                        }
                    }
                    entry<AppRoute.LocalSheet> { route ->
                        Surface(Modifier.fillMaxSize()) {
                            val music = musicPlayer
                            if (music != null) {
                                LocalSheetScreen(
                                    music.library, route.id, { navigator.pop() },
                                    onPlay = { songs, index, source -> music.playback.playList(songs, index, source = source) },
                                    onPlayNext = { music.playback.addNext(it) },
                                    playingKey = playingSong?.key,
                                )
                            }
                        }
                    }
                    entry<AppRoute.LibrarySearch> {
                        Surface(Modifier.fillMaxSize()) {
                            val music = musicPlayer
                            if (library != null && music != null) {
                                LibrarySearchScreen(
                                    library, music.library, nowPlaying, { navigator.pop() }, ::openAlbum,
                                    onOpenSheet = { navigator.push(AppRoute.LocalSheet(it)) },
                                    onOpenStarred = { openMusic(MusicDetailKind.Sheet, it) },
                                    onPlayEpisode = ::playSaved,
                                    onPlaySong = ::playSong,
                                    onPlay = { music.playback.playNow(it, MusicPlaySource(MusicPlaySource.Type.Single)) },
                                    onPlayNext = { music.playback.addNext(it) },
                                )
                            }
                        }
                    }
                    entry<AppRoute.Recent> {
                        Surface(Modifier.fillMaxSize()) {
                            if (library != null) RecentScreen(library, nowPlaying, { navigator.pop() }, ::playSaved, ::playSong)
                        }
                    }
                    entry<AppRoute.LibraryPage> { route ->
                        Surface(Modifier.fillMaxSize()) {
                            val section = LibrarySection.entries.firstOrNull { it.name == route.section }
                            if (library != null && section != null) {
                                LibrarySectionScreen(library, section, { navigator.pop() }, ::openAlbum, ::openAnchor, ::playSaved)
                            }
                        }
                    }
                    entry<AppRoute.Plugins> {
                        Surface(Modifier.fillMaxSize()) { if (plugins != null) PluginsScreen(plugins) { navigator.pop() } }
                    }
                    entry<AppRoute.Accounts> {
                        Surface(Modifier.fillMaxSize()) {
                            AccountsContent(accountState, accounts?.busy == true, accounts?.errorCode,
                                { navigator.pop() }, ::openPhone, { accounts?.setPrimary(it) }, { accounts?.remove(it) },
                                { accounts?.refreshQuota(it) }, { accounts?.retryLoad() }, { openImport(it) },
                                { accounts?.claim(it) }, { accounts?.resumeRewards(it) }, { accounts?.setAutoClaim(it) },
                                importArchive, avatars = accounts?.avatars.orEmpty())
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
                                    recovery = route.recoveryIdentity != null, showPrimaryOption = accountState.hasAccounts,
                                    onPhoneLogin = ::openPhone)
                            }
                        }
                    }
                },
            )
            // Messages show just above the mini player and the bottom bar, never over them.
            com.xuleo.xmplus.md3.ui.components.LocalAppSnackbar.current?.let { host ->
                SnackbarHost(host, Modifier.align(androidx.compose.ui.Alignment.BottomCenter))
            }
            }
            var miniQueue by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
            MiniPlayer(playback, onOpen = ::openPlayer, music = musicPlayer?.playback, onQueue = { miniQueue = true })
            if (importing && musicPlayer != null) {
                ImportSheetSheet(
                    plugins = installedPlugins, library = musicPlayer.library,
                    onImportSheet = musicPlayer.catalog::importSheet, onImportItem = musicPlayer.catalog::importItem,
                    onDismiss = { importing = false },
                    onCreated = { importing = false; navigator.push(AppRoute.LocalSheet(it)) },
                    onPlay = { musicPlayer.playback.playNow(it, MusicPlaySource(MusicPlaySource.Type.Single)) },
                    onPlugins = { importing = false; navigator.push(AppRoute.Plugins) },
                )
            }
            val queueMusic = musicPlayer?.playback
            if (miniQueue && queueMusic != null && playingSong != null) {
                com.xuleo.xmplus.md3.ui.screens.MusicQueueSheet(
                    queueMusic, onClear = { miniQueue = false; queueMusic.clear() }, onDismiss = { miniQueue = false },
                )
            }
        }
    }
    val songActive = playingSong != null
    // The players cover everything, so their messages show in the player itself.
    val playerSnackbar = remember { SnackbarHostState() }
    if (isPlayer && musicPlayer != null && songActive) {
        CompositionLocalProvider(com.xuleo.xmplus.md3.ui.components.LocalAppSnackbar provides playerSnackbar) { MusicPlayerSheet(
            musicPlayer,
            onDismiss = { stack.remove(AppRoute.Player) },
            onOpenMusic = ::openMusic,
        ) }
    } else if (isPlayer && playback != null) {
        CompositionLocalProvider(com.xuleo.xmplus.md3.ui.components.LocalAppSnackbar provides playerSnackbar) { PlayerSheet(
            playback,
            onDismiss = { stack.remove(AppRoute.Player) },
            onOpenAlbum = ::openAlbum,
            onOpenAnchor = ::openAlbumAnchor,
            onAccounts = { navigator.push(AppRoute.Accounts) },
            transcript = transcript,
            libraryActions = libraryActions,
            comments = comments,
            downloads = downloadActions,
        ) }
    }
    accounts?.archive?.let {
        ArchiveDialog(it, accounts.busy, accounts.errorCode, accounts::confirmArchive, accounts::dismissArchive)
    }
}

@Composable
private fun TabContent(destination: Destination, holder: SaveableStateHolder, accounts: AccountViewModel?, discover: DiscoverViewModel?,
    categories: CategoriesViewModel?, rankings: RankingsViewModel?, hotWords: List<HotWord>, links: DiscoverLinks,
    onAccounts: () -> Unit, onLogin: () -> Unit, onImportBackup: () -> Unit, historyUpload: HistoryUploader?,
    library: LibraryViewModel?, downloads: DownloadsViewModel?, onAlbum: (String) -> Unit, onAnchor: (String) -> Unit, onPlay: (String, String, Long) -> Unit,
    onPlayEpisode: (String, String) -> Unit, onPlaySongs: (List<MusicItem>) -> Unit = {}, plugins: PluginsViewModel? = null, onPlugins: () -> Unit = {},
    musicBrowse: MusicBrowseViewModel? = null, onOpenSheet: (MusicSheet, Boolean) -> Unit = { _, _ -> }, onMusicSearch: () -> Unit = {},
    libraryTab: (@Composable () -> Unit)? = null) {
    val installed = plugins?.plugins?.collectAsStateWithLifecycle()?.value
    holder.SaveableStateProvider(destination.name) {
        when (destination) {
            Destination.Discover -> if (discover != null && categories != null && rankings != null) DiscoverScreen(discover, categories, rankings, hotWords, links) else EmptyTab(destination.label, destination.icon)
            Destination.Music -> MusicScreen(if (plugins == null) emptyList() else installed, onPlugins,
                source = plugins?.source?.collectAsStateWithLifecycle()?.value, onSource = { plugins?.selectSource(it) }, browse = musicBrowse, onOpenSheet = onOpenSheet, onSearch = onMusicSearch)
            Destination.Library -> if (library != null && libraryTab != null) libraryTab() else EmptyTab(destination.label, destination.icon)
            Destination.Downloads -> if (downloads != null) DownloadsScreen(downloads, onAlbum, onPlayEpisode, onPlaySongs) else EmptyTab(destination.label, destination.icon)
            Destination.Me -> MeScreen(accounts, onAccounts, onLogin, onImportBackup, historyUpload, installed?.size, onPlugins,
                (LocalContext.current.applicationContext as? XmApplication)?.musicMediaCache,
                (LocalContext.current.applicationContext as? XmApplication)?.musicQuality)
        }
    }
}

@XmDevicePreviews
@Composable
private fun XmAppPreview() = XmPreview { XmAppContent(Destination.Discover, {}) }

@XmPreviews
@Composable
private fun XmAppMePreview() = XmPreview { XmAppContent(Destination.Me, {}) }

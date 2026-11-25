package com.xuleo.xmplus.md3

import android.app.Application
import android.os.Environment
import com.xuleo.xmplus.md3.account.RewardScheduler
import com.xuleo.xmplus.md3.account.XmlyUsageService
import com.xuleo.xmplus.md3.core.account.AccountRepository
import com.xuleo.xmplus.md3.core.account.BalanceLocks
import com.xuleo.xmplus.md3.core.account.UsageLedger
import com.xuleo.xmplus.md3.core.data.KeystoreAccountStore
import com.xuleo.xmplus.md3.core.download.DownloadManager
import com.xuleo.xmplus.md3.core.download.DownloadStore
import com.xuleo.xmplus.md3.core.media.AlbumAudioStore
import com.xuleo.xmplus.md3.core.media.SleepTimerStore
import com.xuleo.xmplus.md3.core.media.AlbumSkipStore
import com.xuleo.xmplus.md3.core.media.HistoryUploader
import com.xuleo.xmplus.md3.core.media.ListenHistoryStore
import com.xuleo.xmplus.md3.core.media.PlaybackBookmarkStore
import com.xuleo.xmplus.md3.core.media.PlaybackController
import com.xuleo.xmplus.md3.core.media.PlaybackOwner
import com.xuleo.xmplus.md3.core.media.PlaybackProgressStore
import com.xuleo.xmplus.md3.core.media.PlaybackSessionStore
import com.xuleo.xmplus.md3.core.plugin.MusicCatalog
import com.xuleo.xmplus.md3.core.plugin.PluginManager
import com.xuleo.xmplus.md3.core.protocol.XmlyAlbum
import com.xuleo.xmplus.md3.core.protocol.XmlyAnchor
import com.xuleo.xmplus.md3.core.protocol.XmlyBrowse
import com.xuleo.xmplus.md3.core.protocol.XmlyComments
import com.xuleo.xmplus.md3.core.protocol.XmlyDiscovery
import com.xuleo.xmplus.md3.core.protocol.XmlyHistory
import com.xuleo.xmplus.md3.core.protocol.XmlyHome
import com.xuleo.xmplus.md3.core.protocol.XmlyHttp
import com.xuleo.xmplus.md3.core.protocol.XmlyLibrary
import com.xuleo.xmplus.md3.core.protocol.XmlyLogin
import com.xuleo.xmplus.md3.core.protocol.XmlyPlayback
import com.xuleo.xmplus.md3.core.protocol.XmlyProfile
import com.xuleo.xmplus.md3.core.protocol.XmlyQuotaClient
import com.xuleo.xmplus.md3.core.protocol.XmlyRewards
import com.xuleo.xmplus.md3.core.protocol.XmlySearch
import com.xuleo.xmplus.md3.core.protocol.XmlySkip
import com.xuleo.xmplus.md3.core.protocol.XmlyTranscript
import com.xuleo.xmplus.md3.download.DownloadActions
import com.xuleo.xmplus.md3.library.BookmarkNames
import com.xuleo.xmplus.md3.library.CommentActions
import com.xuleo.xmplus.md3.library.LibraryActions
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

class XmApplication : Application(), PlaybackOwner {
    /** Work that belongs to the process rather than to a screen. */
    private val processScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** The one door to the XMPlus API hosts, shared by every account. */
    val http = XmlyHttp()

    private val quotaClient by lazy { XmlyQuotaClient(http) }

    val home by lazy { XmlyHome(http) }
    val browse by lazy { XmlyBrowse(http) }
    val discovery by lazy { XmlyDiscovery(http) }
    val search by lazy { XmlySearch(http) }
    val album by lazy { XmlyAlbum(http) }
    val playbackApi by lazy { XmlyPlayback(http) }
    val anchor by lazy { XmlyAnchor(http) }
    val profile by lazy { XmlyProfile(http) }

    /** Keywords searched on this device; never part of a backup. */
    val searchHistory by lazy { com.xuleo.xmplus.md3.browse.SearchHistoryStore(this) }
    val musicSearchHistory by lazy {
        com.xuleo.xmplus.md3.browse.SearchHistoryStore(this, com.xuleo.xmplus.md3.browse.SearchHistoryStore.MusicKey)
    }
    val transcript by lazy { XmlyTranscript(http) }
    val library by lazy { XmlyLibrary(http) }
    val libraryActions by lazy { LibraryActions(accounts, library) }
    val comments by lazy { XmlyComments(http) }
    val commentActions by lazy { CommentActions(accounts, comments) }

    /** Cloud listening history; it reads and writes progress and never spends listening time. */
    val history by lazy { XmlyHistory(http) }

    /** 听过 on this device; written by playback and read by the Library tab. */
    val listens by lazy { ListenHistoryStore(this) }

    /** Bookmarks on this device, shared by playback and the 书签 list. */
    val bookmarks by lazy { PlaybackBookmarkStore(this) }

    /** Fills in the names of bookmarks made before they were kept, one lookup per episode for each launch. */
    val bookmarkNames by lazy { BookmarkNames(bookmarks, album, playbackApi) }

    /** Claims and debits change the same balance, so they share one lock per account. */
    private val balanceLocks = BalanceLocks()

    val accounts: AccountRepository by lazy {
        AccountRepository(KeystoreAccountStore(this), quotaClient::queryQuota, XmlyRewards(http)::claim, balanceLocks)
    }

    /** Every charge for listening time goes through this ledger. */
    val usage: UsageLedger by lazy { UsageLedger(accounts, XmlyUsageService(quotaClient), balanceLocks) }

    /** Episodes kept for offline listening: the app's music folder on external storage, or the app's own files without it. */
    val downloads: DownloadManager by lazy {
        val folder = getExternalFilesDir(Environment.DIRECTORY_MUSIC)?.let { File(it, "XMPlus") } ?: File(filesDir, "XMPlus-downloads")
        DownloadManager(folder, DownloadStore(File(filesDir, "downloads/tasks.json")), accounts, playbackApi, usage, processScope)
    }

    val downloadActions by lazy { DownloadActions(accounts, album, playbackApi, usage, downloads) }

    // Resolve an interrupted debit locally before allowing a new playback to charge.
    private val usageReady by lazy {
        processScope.async {
            accounts.load()
            usage.load()
            downloads.start()
        }
    }
    override val playback by lazy {
        PlaybackController(
            accounts, usage, playbackApi, album, PlaybackProgressStore(this), history,
            HistoryUploader(this, accounts, history), bookmarks,
            AlbumSkipStore(this), AlbumAudioStore(this), SleepTimerStore(this), XmlySkip(http), PlaybackSessionStore(this), listens, downloads,
        ) { usageReady.await() }
    }

    /** One login session per process: it owns the SMS cooldowns and the pending challenge. */
    val login: XmlyLogin by lazy { XmlyLogin() }

    /** Music plugins. Their own client, so Ximalaya's headers and cookies never reach a plugin's requests. */
    val plugins: PluginManager by lazy { PluginManager(this, OkHttpClient()) }

    /** What 音乐 reads from the plugins: sheets, rankings, search and details. */
    val musicCatalog: MusicCatalog by lazy { MusicCatalog(plugins) }

    override fun onCreate() {
        super.onCreate()
        RewardScheduler(accounts, processScope)
        usageReady.start()
        processScope.launch { plugins.start() }
    }
}

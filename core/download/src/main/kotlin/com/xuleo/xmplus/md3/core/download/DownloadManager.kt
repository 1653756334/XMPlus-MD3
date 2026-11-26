package com.xuleo.xmplus.md3.core.download

import com.xuleo.xmplus.md3.core.account.AccountRepository
import com.xuleo.xmplus.md3.core.account.DebitState
import com.xuleo.xmplus.md3.core.account.UsageLedger
import com.xuleo.xmplus.md3.core.model.ContentKind
import com.xuleo.xmplus.md3.core.model.MusicItem
import com.xuleo.xmplus.md3.core.model.Track
import com.xuleo.xmplus.md3.core.model.XmlyException
import com.xuleo.xmplus.md3.core.protocol.XmlyPlayback
import com.xuleo.xmplus.md3.core.protocol.validatePlaybackUrl
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * Downloads episodes into [folder]. Each episode is one task, keyed by its track id.
 *
 * - A paid episode is charged through the usage ledger before its bytes are requested, and only with the listening time
 *   the listener confirmed when it was queued. The charge is keyed by the task id, so a retry gets the confirmed receipt
 *   back and never charges again.
 * - Pause and cancel stop the transfer at once. A paused transfer keeps its partial file and continues with a range
 *   request.
 * - Tasks are saved in [store]. After a restart nothing runs by itself: unfinished tasks wait as paused.
 * - Songs (from M5) are tasks of [ContentKind.Song], keyed by their `mf:platform:id`. They go through [songSource] and
 *   never touch the ledger, accounts or confirmed listening time. A finished song gets its tags, cover and lyric written
 *   from [songLyric] and its artwork; that never fails the download.
 */
class DownloadManager(
    private val folder: File,
    private val store: DownloadStore,
    private val accounts: AccountRepository,
    private val playback: XmlyPlayback,
    private val ledger: UsageLedger,
    private val scope: CoroutineScope,
    /** Where a song downloads from. Without it a song task fails; 听书 never calls it. */
    private val songSource: SongResolver = SongResolver { _, _ -> null },
    /** The lyric text written into a finished song file. */
    private val songLyric: suspend (MusicItem) -> String? = { null },
    /** The quality a song takes when it is queued without one: the download quality setting. */
    private val songQuality: () -> String = { "standard" },
    private val clock: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val lock = Mutex()
    private val tasks = LinkedHashMap<String, DownloadTask>()
    private val jobs = HashMap<String, Job>()
    private val published = MutableStateFlow<List<DownloadTask>>(emptyList())
    private val publishedSongs = MutableStateFlow<List<DownloadTask>>(emptyList())

    /** Every task, in the order they were added. */
    val list: StateFlow<List<DownloadTask>> = published.asStateFlow()

    /** Song tasks only, in the order they were queued. */
    val songs: StateFlow<List<DownloadTask>> = publishedSongs.asStateFlow()

    /** Same rules as playback: audio hosts only, no account headers, and every redirect hop checked again. */
    private val http = OkHttpClient.Builder()
        .followRedirects(false).followSslRedirects(false).cookieJar(CookieJar.NO_COOKIES)
        .retryOnConnectionFailure(false).addInterceptor(AudioRedirects)
        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
        .build()

    /** Songs go anywhere a plugin points, with their own headers and redirects; no Ximalaya rules apply. */
    private val songHttp = OkHttpClient.Builder()
        .cookieJar(CookieJar.NO_COOKIES).retryOnConnectionFailure(true)
        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
        .build()

    /** Loads the saved list. Whatever was running is paused, since the process that ran it has gone. */
    suspend fun start() = lock.withLock {
        for (task in store.load()) {
            tasks[task.trackId] = when (task.state) {
                DownloadState.Queued, DownloadState.Running ->
                    task.copy(state = DownloadState.Paused, bytes = partialLength(task.taskId))
                DownloadState.Completed ->
                    if (target(task.file.orEmpty()).isFile) task else task.copy(state = DownloadState.Failed, error = "file_missing")
                else -> task
            }
        }
        commitLocked()
    }

    /** The finished file for an episode, for playback. Null when it is not downloaded. */
    fun completedFile(trackId: String): File? {
        val task = published.value.firstOrNull { it.trackId == trackId && it.state == DownloadState.Completed } ?: return null
        return task.file?.let { target(it) }?.takeIf { it.isFile }
    }

    /** The finished file for a song, so it plays without a plugin (MP-01). Null when it is not downloaded. */
    fun completedSongFile(song: MusicItem): File? = published.value
        .firstOrNull { it.trackId == song.key && it.kind == ContentKind.Song && it.state == DownloadState.Completed }
        ?.file?.let { target(it) }?.takeIf { it.isFile }

    /** A song's task, whatever its state; null when it was never queued. */
    fun songTask(song: MusicItem): DownloadTask? =
        published.value.firstOrNull { it.trackId == song.key && it.kind == ContentKind.Song }

    /**
     * Queues [requests] of one album and returns how many were added. Episodes already waiting, running, paused or
     * downloaded are left alone; failed ones queue again, with the listening time confirmed this time.
     */
    suspend fun enqueue(
        albumId: String,
        albumTitle: String,
        coverUrl: String?,
        albumTotal: Int,
        requests: List<DownloadRequest>,
    ): Int = lock.withLock {
        val now = clock()
        var added = 0
        for ((track, confirmed) in requests) {
            val existing = tasks[track.id]
            when {
                existing == null -> tasks[track.id] = DownloadTask(
                    trackId = track.id,
                    taskId = newId(),
                    albumId = albumId,
                    albumTitle = albumTitle,
                    title = track.title,
                    index = track.officialPosition ?: track.orderNo,
                    albumTotal = albumTotal,
                    durationSeconds = track.durationSeconds,
                    state = DownloadState.Queued,
                    createdAt = now,
                    coverUrl = coverUrl,
                    confirmedSeconds = confirmed,
                )
                existing.state == DownloadState.Failed -> tasks[track.id] = existing.copy(
                    state = DownloadState.Queued,
                    error = null,
                    coverUrl = coverUrl ?: existing.coverUrl,
                    confirmedSeconds = confirmed ?: existing.confirmedSeconds,
                )
                else -> continue
            }
            added++
        }
        pumpLocked()
        commitLocked()
        added
    }

    /**
     * Queues a song at [quality], or at the download quality setting when it is null. True when it was added.
     * A song already waiting, running, paused or downloaded is left alone; a failed one queues again. Nothing is
     * charged: [DownloadTask.accountIdentity], [DownloadTask.receiptId] and [DownloadTask.confirmedSeconds] stay null.
     */
    suspend fun enqueueSong(song: MusicItem, quality: String? = null): Boolean = lock.withLock {
        val existing = tasks[song.key]
        when {
            existing == null -> tasks[song.key] = DownloadTask(
                trackId = song.key,
                taskId = newId(),
                // A song's platform and artist stand where an episode's album and title do, for the shared lists.
                albumId = song.platform,
                albumTitle = song.artist,
                title = song.title,
                index = null,
                albumTotal = 0,
                durationSeconds = song.durationSeconds,
                state = DownloadState.Queued,
                createdAt = clock(),
                coverUrl = song.artwork,
                kind = ContentKind.Song,
                quality = quality ?: songQuality(),
                item = song.raw.toString(),
            )
            existing.state == DownloadState.Failed -> tasks[song.key] = existing.copy(
                state = DownloadState.Queued,
                error = null,
                coverUrl = song.artwork ?: existing.coverUrl,
                quality = quality ?: existing.quality ?: songQuality(),
                item = song.raw.toString(),
            )
            else -> return@withLock false
        }
        pumpLocked()
        commitLocked()
        true
    }

    /** Stops a waiting or running episode. Its partial file stays, so [resume] continues where it stopped. */
    suspend fun pause(trackId: String) {
        val job = lock.withLock {
            val task = tasks[trackId] ?: return
            if (task.state != DownloadState.Queued && task.state != DownloadState.Running) return
            tasks[trackId] = task.copy(state = DownloadState.Paused)
            commitLocked()
            jobs[trackId]
        }
        job?.cancelAndJoin()
    }

    /** Queues a paused or failed episode again. A failed one keeps its task id, so its charge is never repeated. */
    suspend fun resume(trackId: String) = lock.withLock {
        val task = tasks[trackId] ?: return@withLock
        if (task.state == DownloadState.Paused || task.state == DownloadState.Failed) {
            tasks[trackId] = task.copy(state = DownloadState.Queued, error = null)
            pumpLocked()
            commitLocked()
        }
    }

    /** Removes an episode that is not downloaded yet, with its partial file. */
    suspend fun cancel(trackId: String) {
        val (job, taskId) = lock.withLock {
            val task = tasks[trackId] ?: return
            if (task.state == DownloadState.Completed) return
            tasks.remove(trackId)
            commitLocked()
            jobs[trackId] to task.taskId
        }
        job?.cancelAndJoin()
        partial(taskId).delete()
    }

    /** Removes a downloaded episode and its audio file. */
    suspend fun delete(trackId: String) = lock.withLock {
        val task = tasks[trackId] ?: return@withLock
        if (task.state != DownloadState.Completed) return@withLock
        tasks.remove(trackId)
        commitLocked()
        task.file?.let { target(it).delete() }
    }

    /** Removes a downloaded song and its audio file. */
    suspend fun deleteSong(song: MusicItem) = delete(song.key)

    /** Removes every song task: downloaded songs with their files, unfinished ones with their partial files. */
    suspend fun deleteSongs() {
        val removed = lock.withLock {
            val gone = tasks.values.filter { it.kind == ContentKind.Song }
            if (gone.isEmpty()) return
            gone.forEach { tasks.remove(it.trackId) }
            commitLocked()
            gone.map { jobs[it.trackId] to it }
        }
        removed.forEach { (job, _) -> job?.cancelAndJoin() }
        removed.forEach { (_, task) ->
            partial(task.taskId).delete()
            task.file?.let { target(it).delete() }
        }
    }

    /** Removes every downloaded episode of one album, with their audio files. */
    suspend fun deleteAlbum(albumId: String) = lock.withLock {
        val gone = tasks.values.filter { it.albumId == albumId && it.kind == ContentKind.Episode && it.state == DownloadState.Completed }
        if (gone.isEmpty()) return@withLock
        gone.forEach { tasks.remove(it.trackId) }
        commitLocked()
        gone.forEach { task -> task.file?.let { target(it).delete() } }
    }

    /**
     * Pauses everything waiting or running in one step, so a slot freed by one pause is not handed to an episode that
     * is about to be paused too (and might be charged first).
     */
    suspend fun pauseAll() {
        val running = lock.withLock {
            val active = tasks.values.filter { it.state == DownloadState.Queued || it.state == DownloadState.Running }
            if (active.isEmpty()) return
            active.forEach { tasks[it.trackId] = it.copy(state = DownloadState.Paused) }
            commitLocked()
            active.mapNotNull { jobs[it.trackId] }
        }
        running.forEach { it.cancelAndJoin() }
    }

    /** Removes every episode that is not downloaded yet, with the partial files, in one step for the same reason. */
    suspend fun cancelAll() {
        val removed = lock.withLock {
            val unfinished = tasks.values.filter { it.state != DownloadState.Completed }
            if (unfinished.isEmpty()) return
            unfinished.forEach { tasks.remove(it.trackId) }
            commitLocked()
            unfinished.map { jobs[it.trackId] to it.taskId }
        }
        removed.forEach { (job, _) -> job?.cancelAndJoin() }
        removed.forEach { (_, taskId) -> partial(taskId).delete() }
    }

    /** Free space where the downloads are kept. */
    fun freeBytes(): Long = generateSequence(folder) { it.parentFile }.firstOrNull { it.exists() }?.usableSpace ?: 0L

    suspend fun resumeAll() {
        val ids = lock.withLock { tasks.values.filter { it.state == DownloadState.Paused || it.state == DownloadState.Failed }.map { it.trackId } }
        for (id in ids) resume(id)
    }

    private fun pumpLocked() {
        while (jobs.size < Concurrency) {
            val next = tasks.values.firstOrNull { it.state == DownloadState.Queued } ?: return
            tasks[next.trackId] = next.copy(state = DownloadState.Running, error = null)
            jobs[next.trackId] = scope.launch { run(next.trackId) }
        }
    }

    /** Runs one task to completion, retrying transient failures. A pause or cancel ends the run without a state change. */
    private suspend fun run(trackId: String) {
        var failures = 0
        try {
            while (true) {
                try {
                    val file = transfer(trackId)
                    complete(trackId, file)
                    // Tags are written after the file is complete, so a slow cover or lyric never holds the download back.
                    if (lock.withLock { tasks[trackId] }?.kind == ContentKind.Song) tag(trackId, file)
                    return
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    currentCoroutineContext().ensureActive()
                    failures++
                    val code = errorCode(error)
                    if (!retryable(error) || failures > MaxRetries) {
                        fail(trackId, code)
                        return
                    }
                    delay(if (code == "rate_limited") RateLimitWait else RetryBackoff * failures)
                }
            }
        } finally {
            withContext(NonCancellable) {
                lock.withLock {
                    jobs.remove(trackId)
                    pumpLocked()
                    commitLocked()
                }
            }
        }
    }

    /** One attempt: an episode is priced first when it needs listening time; a song is not. Both stream into the partial file. */
    private suspend fun transfer(trackId: String): String {
        val task = lock.withLock { tasks[trackId] } ?: throw CancellationException("download removed")
        return if (task.kind == ContentKind.Song) transferSong(task) else transferEpisode(task)
    }

    /** One attempt of an episode: priced first when it needs listening time, then streamed into the partial file. */
    private suspend fun transferEpisode(task: DownloadTask): String {
        val trackId = task.trackId
        val index = task.index ?: throw XmlyException("missing_index", definitive = true)
        val credentials = accounts.primaryCredentials()
        val authorization = playback.resolve(task.trackId, credentials, ledger::queryQuota)
        val url = authorization.url ?: throw XmlyException("invalid_media", definitive = true)
        if (authorization.requiresListeningTime) {
            // Only what the listener agreed to is charged. An episode the preview showed as free stops here with
            // confirmation_required, unless an earlier attempt already holds its receipt.
            val receipt = ledger.prepay(
                identity = authorization.accountIdentity,
                taskId = task.taskId,
                trackId = task.trackId,
                durationSeconds = task.durationSeconds,
                requiresReward = true,
                userConfirmed = task.confirmedSeconds != null,
                expectedSeconds = task.confirmedSeconds,
            )
            val confirmed = receipt?.takeIf { it.state == DebitState.Confirmed }
                ?: throw XmlyException("receipt_required", definitive = true)
            priced(trackId, authorization.accountIdentity, confirmed.operationId)
        }
        folder.mkdirs()
        if (folder.usableSpace < MinFreeBytes) throw XmlyException("disk_space", definitive = true)
        val partial = partial(task.taskId)
        partial.parentFile?.mkdirs()
        val existing = partialLength(task.taskId)
        download(trackId, http, episodeRequest(url, existing), partial, existing)
        if (partial.length() == 0L) throw IOException("empty audio")
        val extension = url.encodedPath.substringAfterLast('.', "").lowercase().takeIf { it in AudioExtensions } ?: "m4a"
        val directory = File(folder, DownloadNames.album(task.albumTitle, task.albumId)).apply { mkdirs() }
        val name = DownloadNames.episode(DownloadNames.index(index, task.albumTotal), task.title, task.trackId, extension)
        val target = File(directory, name)
        // The name carries the track id, so an existing file of that name is this same episode (DL-21: skip).
        if (target.isFile && target.length() > 0) {
            partial.delete()
        } else {
            Files.move(partial.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        return "${directory.name}/$name"
    }

    /** One attempt of a song: its link from the plugins, streamed into the partial file, named after the song. */
    private suspend fun transferSong(task: DownloadTask): String {
        val song = task.song() ?: throw XmlyException("invalid_item", definitive = true)
        val source = songSource.resolve(song, task.quality ?: songQuality())
            ?: throw XmlyException("no_source", definitive = true)
        folder.mkdirs()
        if (folder.usableSpace < MinFreeBytes) throw XmlyException("disk_space", definitive = true)
        val partial = partial(task.taskId)
        partial.parentFile?.mkdirs()
        val existing = partialLength(task.taskId)
        val request = Request.Builder().url(source.url)
            .apply { source.headers.forEach { (name, value) -> header(name, value) } }
            .apply { if (existing > 0) header("Range", "bytes=$existing-") }
            .build()
        val type = download(task.trackId, songHttp, request, partial, existing)
        if (partial.length() == 0L) throw IOException("empty audio")
        val extension = extension(source.url) ?: typeExtension(type) ?: "mp3"
        val directory = File(folder, DownloadNames.SongsFolder).apply { mkdirs() }
        val name = DownloadNames.song(task.albumTitle, task.title, song.id, extension)
        val target = File(directory, name)
        // The name carries the song's id, so an existing file of that name is this same song.
        if (target.isFile && target.length() > 0) {
            partial.delete()
        } else {
            Files.move(partial.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        return "${directory.name}/$name"
    }

    /** Streams [url] into [partial], continuing from [existing] bytes when the server agrees to a range request. */
    private fun episodeRequest(url: HttpUrl, existing: Long): Request =
        Request.Builder().url(url).apply { if (existing > 0) header("Range", "bytes=$existing-") }.build()

    /** Streams one request into [partial], continuing from [existing] bytes. Returns the response's content type. */
    private suspend fun download(trackId: String, client: OkHttpClient, request: Request, partial: File, existing: Long): String? =
        withContext(Dispatchers.IO) {
            val call = client.newCall(request)
            coroutineScope {
                // A pause or cancel cancels this coroutine; the watcher then cancels the call, so a blocked read stops at once.
                val watcher = launch(start = CoroutineStart.UNDISPATCHED) {
                    try {
                        awaitCancellation()
                    } finally {
                        call.cancel()
                    }
                }
                try {
                    call.execute().use { response ->
                        when (response.code) {
                            200, 206 -> Unit
                            416 -> {
                                partial.delete()
                                throw IOException("range not satisfiable")
                            }
                            429 -> throw XmlyException("rate_limited")
                            401, 403 -> throw XmlyException("link_expired")
                            in 400..499 -> throw XmlyException("http_error", definitive = true)
                            else -> throw IOException("http ${response.code}")
                        }
                        val type = response.body.contentType()?.toString()
                        val resumed = response.code == 206
                        val start = if (resumed) existing else 0L
                        val total = response.body.contentLength().takeIf { it >= 0 }?.let { it + start }
                        FileOutputStream(partial, resumed).use { output ->
                            response.body.byteStream().use { input ->
                                val buffer = ByteArray(BufferSize)
                                var written = start
                                var reported = start
                                while (true) {
                                    val read = input.read(buffer)
                                    if (read < 0) break
                                    output.write(buffer, 0, read)
                                    written += read
                                    if (written - reported >= ReportEvery) {
                                        report(trackId, written, total)
                                        reported = written
                                    }
                                }
                                if (total != null && written < total) throw IOException("short read")
                                report(trackId, written, total)
                            }
                        }
                        type
                    }
                } finally {
                    watcher.cancel()
                }
            }
        }

    /**
     * Writes a finished song's tags, cover and lyric into its file, then keeps the file whatever happens: a song without
     * tags still plays. Runs off the main thread, after the transfer.
     */
    private suspend fun tag(trackId: String, file: String) {
        val task = lock.withLock { tasks[trackId] } ?: return
        val song = task.song() ?: return
        try {
            val lyric = try {
                songLyric(song)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            SongTags.write(target(file), task.title, task.albumTitle, song.album, cover(song.artwork), lyric)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // The song plays without its tags.
        }
    }

    /** The song's artwork, for the file's cover. Null when the link is missing, not an image or too large. */
    private suspend fun cover(url: String?): ByteArray? {
        if (url.isNullOrBlank()) return null
        return withContext(Dispatchers.IO) {
            try {
                songHttp.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    if (response.body.contentType()?.toString()?.startsWith("image/") != true) return@use null
                    if (response.body.contentLength() > MaxCoverBytes) return@use null
                    response.body.bytes().takeIf { it.isNotEmpty() && it.size <= MaxCoverBytes }
                }
            } catch (_: Exception) {
                null
            }
        }
    }

    private suspend fun priced(trackId: String, identity: String, receipt: String) = lock.withLock {
        update(trackId) { it.copy(accountIdentity = identity, receiptId = receipt) }
        commitLocked()
    }

    private suspend fun report(trackId: String, bytes: Long, total: Long?) = lock.withLock {
        update(trackId) { it.copy(bytes = bytes, totalBytes = total) }
        publish()
    }

    private suspend fun complete(trackId: String, file: String) = lock.withLock {
        val size = target(file).length()
        update(trackId) { it.copy(state = DownloadState.Completed, file = file, bytes = size, totalBytes = size, error = null) }
        commitLocked()
    }

    private suspend fun fail(trackId: String, code: String) = lock.withLock {
        update(trackId) { it.copy(state = DownloadState.Failed, error = code) }
        commitLocked()
    }

    private fun update(trackId: String, change: (DownloadTask) -> DownloadTask) {
        tasks[trackId]?.let { tasks[trackId] = change(it) }
    }

    private fun commitLocked() {
        store.save(tasks.values)
        publish()
    }

    private fun publish() {
        published.value = tasks.values.toList()
        publishedSongs.value = published.value.filter { it.kind == ContentKind.Song }
    }

    private fun target(relative: String) = File(folder, relative)

    private fun partial(taskId: String) = File(folder, ".partial/$taskId.part")

    private fun partialLength(taskId: String): Long = partial(taskId).let { if (it.isFile) it.length() else 0L }

    private fun retryable(error: Exception) = when (error) {
        is XmlyException -> !error.definitive
        is IOException -> true
        else -> false
    }

    private fun errorCode(error: Exception) = when (error) {
        is XmlyException -> error.code
        is IOException -> "network"
        else -> "unknown"
    }

    /** The extension a link's path ends with, when it is one this app plays; MusicFree's `getExtensionName`. */
    private fun extension(url: String): String? {
        val path = url.toHttpUrlOrNull()?.encodedPath
        path?.substringAfterLast('.', "")?.lowercase()?.takeIf { it in SongExtensions }?.let { return it }
        return url.substringBefore('?').substringAfterLast('.', "").lowercase().takeIf { it in SongExtensions }
    }

    /** The extension a response's content type stands for, when the link gave none. */
    private fun typeExtension(type: String?): String? = when (type?.substringBefore(';')?.trim()?.lowercase()) {
        "audio/mpeg", "audio/mp3" -> "mp3"
        "audio/flac", "audio/x-flac" -> "flac"
        "audio/mp4", "audio/m4a", "audio/x-m4a" -> "m4a"
        "audio/ogg", "application/ogg" -> "ogg"
        "audio/aac" -> "aac"
        "audio/wav", "audio/x-wav" -> "wav"
        else -> null
    }

    private companion object {
        const val Concurrency = 3
        const val MaxRetries = 3
        const val MinFreeBytes = 512L * 1024 * 1024
        const val BufferSize = 64 * 1024
        const val ReportEvery = 256L * 1024
        const val MaxCoverBytes = 8L * 1024 * 1024
        val RateLimitWait = 5.minutes
        val RetryBackoff = 5.seconds
        val AudioExtensions = setOf("m4a", "mp3", "aac")

        /** What a song may be kept as; anything else is kept as an mp3, as MusicFree does. */
        val SongExtensions = setOf("mp3", "flac", "m4a", "ogg", "aac", "wav", "wma", "opus")
    }
}

/** The playback redirect rules, applied to downloads: audio hosts only, no account headers, six hops at most. */
private object AudioRedirects : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()
        repeat(6) { hop ->
            val url = try {
                validatePlaybackUrl(request.url.toString())
            } catch (failure: XmlyException) {
                throw IOException("Invalid audio source", failure)
            }
            request = request.newBuilder().url(url)
                .removeHeader("Cookie").removeHeader("Authorization").removeHeader("x-tk").build()
            val response = chain.proceed(request)
            if (response.code !in RedirectCodes) return response
            val next = response.use { reply ->
                reply.header("Location")?.takeIf(String::isNotBlank)?.let(url::resolve)
            }
            if (next == null || hop == 5) throw IOException("Invalid audio redirect")
            request = request.newBuilder().url(next).build()
        }
        error("Unreachable redirect limit")
    }

    private val RedirectCodes = setOf(301, 302, 303, 307, 308)
}
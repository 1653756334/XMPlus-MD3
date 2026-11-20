package com.xuleo.xmplus.md3.core.download

import com.xuleo.xmplus.md3.core.account.AccountRepository
import com.xuleo.xmplus.md3.core.account.DebitState
import com.xuleo.xmplus.md3.core.account.UsageLedger
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
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * Downloads episodes into [folder]. Each episode is one task, keyed by its track id.
 *
 * - A paid episode is charged through the usage ledger before its bytes are requested. The charge is keyed by the task
 *   id, so a retry gets the confirmed receipt back and never charges again.
 * - Pause and cancel stop the transfer at once. A paused transfer keeps its partial file and continues with a range
 *   request.
 * - Tasks are saved in [store]. After a restart nothing runs by itself: unfinished tasks wait as paused.
 */
class DownloadManager(
    private val folder: File,
    private val store: DownloadStore,
    private val accounts: AccountRepository,
    private val playback: XmlyPlayback,
    private val ledger: UsageLedger,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val lock = Mutex()
    private val tasks = LinkedHashMap<String, DownloadTask>()
    private val jobs = HashMap<String, Job>()
    private val published = MutableStateFlow<List<DownloadTask>>(emptyList())

    /** Every task, in the order they were added. */
    val list: StateFlow<List<DownloadTask>> = published.asStateFlow()

    /** Same rules as playback: audio hosts only, no account headers, and every redirect hop checked again. */
    private val http = OkHttpClient.Builder()
        .followRedirects(false).followSslRedirects(false).cookieJar(CookieJar.NO_COOKIES)
        .retryOnConnectionFailure(false).addInterceptor(AudioRedirects)
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

    /** Queues [tracks] of one album. Episodes already waiting, running, paused or downloaded are left alone; failed ones queue again. */
    suspend fun enqueue(albumId: String, albumTitle: String, albumTotal: Int, tracks: List<Track>) = lock.withLock {
        val now = clock()
        for (track in tracks) {
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
                )
                existing.state == DownloadState.Failed -> tasks[track.id] = existing.copy(state = DownloadState.Queued, error = null)
                else -> Unit
            }
        }
        pumpLocked()
        commitLocked()
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

    suspend fun pauseAll() {
        val ids = lock.withLock { tasks.values.filter { it.state == DownloadState.Queued || it.state == DownloadState.Running }.map { it.trackId } }
        for (id in ids) pause(id)
    }

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

    /** Runs one episode to completion, retrying transient failures. A pause or cancel ends the run without a state change. */
    private suspend fun run(trackId: String) {
        var failures = 0
        try {
            while (true) {
                try {
                    complete(trackId, transfer(trackId))
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

    /** One attempt: priced first when the episode needs listening time, then streamed into the partial file. */
    private suspend fun transfer(trackId: String): String {
        val task = lock.withLock { tasks[trackId] } ?: throw CancellationException("download removed")
        val index = task.index ?: throw XmlyException("missing_index", definitive = true)
        val credentials = accounts.primaryCredentials()
        val authorization = playback.resolve(task.trackId, credentials, ledger::queryQuota)
        val url = authorization.url ?: throw XmlyException("invalid_media", definitive = true)
        if (authorization.requiresListeningTime) {
            val receipt = ledger.prepay(
                identity = authorization.accountIdentity,
                taskId = task.taskId,
                trackId = task.trackId,
                durationSeconds = task.durationSeconds,
                requiresReward = true,
                userConfirmed = true,
            )
            val confirmed = receipt?.takeIf { it.state == DebitState.Confirmed }
                ?: throw XmlyException("receipt_required", definitive = true)
            priced(trackId, authorization.accountIdentity, confirmed.operationId)
        }
        folder.mkdirs()
        if (folder.usableSpace < MinFreeBytes) throw XmlyException("disk_space", definitive = true)
        val partial = partial(task.taskId)
        partial.parentFile?.mkdirs()
        download(trackId, url, partial, partialLength(task.taskId))
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

    /** Streams [url] into [partial], continuing from [existing] bytes when the server agrees to a range request. */
    private suspend fun download(trackId: String, url: HttpUrl, partial: File, existing: Long) {
        withContext(Dispatchers.IO) {
            val call = http.newCall(
                Request.Builder().url(url).apply { if (existing > 0) header("Range", "bytes=$existing-") }.build(),
            )
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
                    }
                } finally {
                    watcher.cancel()
                }
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

    private companion object {
        const val Concurrency = 3
        const val MaxRetries = 3
        const val MinFreeBytes = 512L * 1024 * 1024
        const val BufferSize = 64 * 1024
        const val ReportEvery = 256L * 1024
        val RateLimitWait = 5.minutes
        val RetryBackoff = 5.seconds
        val AudioExtensions = setOf("m4a", "mp3", "aac")
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
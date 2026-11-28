package com.xuleo.xmplus.md3.core.media

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.cache.CacheDataSink
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy.LoadErrorInfo
import com.xuleo.xmplus.md3.core.model.XmlyException
import com.xuleo.xmplus.md3.core.protocol.validatePlaybackUrl
import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.CookieJar
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response

/**
 * No account headers, no general redirects, and a separate connection pool from the API. [cache] keeps the episodes
 * heard lately on disk: what is there plays while the rest is fetched and written, so hearing one again is offline.
 */
@UnstableApi
internal fun createPlayer(context: Context, cache: EpisodeMediaCache? = null): ExoPlayer {
    val dataSource = audioDataSource()
    val sources = mediaSources(context, dataSource, cache)
    val media = ProgressiveMediaSource.Factory(sources).setLoadErrorHandlingPolicy(
        object : DefaultLoadErrorHandlingPolicy() {
            override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorInfo): Long =
                if (loadErrorInfo.exception.hasExpiredAuthorization()) C.TIME_UNSET
                else super.getRetryDelayMsFor(loadErrorInfo)
        },
    )
    return ExoPlayer.Builder(context, media)
        .setAudioAttributes(SpeechAttributes, true)
        .setHandleAudioBecomingNoisy(true)
        .setWakeMode(C.WAKE_MODE_NETWORK)
        .setSeekBackIncrementMs(PlaybackSkipMillis)
        .setSeekForwardIncrementMs(PlaybackSkipMillis)
        .build()
}

/**
 * The audio requests themselves: no account headers, no general redirects, and a pool of their own. Playback and the
 * caching of the next episode go through the same rules.
 */
@UnstableApi
internal fun audioDataSource(): OkHttpDataSource.Factory {
    val client = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .cookieJar(CookieJar.NO_COOKIES)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .addInterceptor(PlaybackRedirects)
        .build()
    return OkHttpDataSource.Factory(client).setUserAgent("XMPlus/1.0")
        .setDefaultRequestProperties(mapOf("Accept" to "audio/*, application/octet-stream"))
}

/**
 * What episodes are read through: the disk first, then the network, with what is read written back. Without a cache it
 * is the network alone. Exposed so a prefetched episode is written with the same redirects and headers as a played one.
 */
@UnstableApi
internal fun cacheSourceFactory(
    context: Context,
    dataSource: OkHttpDataSource.Factory,
    cache: EpisodeMediaCache,
): CacheDataSource.Factory = CacheDataSource.Factory()
    .setCache(cache.cache)
    .setUpstreamDataSourceFactory(DefaultDataSource.Factory(context, dataSource))
    .setCacheWriteDataSinkFactory(CacheDataSink.Factory().setCache(cache.cache))
    .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

@UnstableApi
private fun mediaSources(
    context: Context,
    dataSource: OkHttpDataSource.Factory,
    cache: EpisodeMediaCache?,
): DataSource.Factory = cache?.let { cacheSourceFactory(context, dataSource, it) }
    ?: DefaultDataSource.Factory(context, dataSource)

/** Spoken audio: another app's short sound pauses the story instead of talking over it. */
internal val SpeechAttributes: AudioAttributes =
    AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build()

/** Music: another app's short sound lowers the song for a moment instead of pausing it. */
internal val MusicAttributes: AudioAttributes =
    AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build()

private object PlaybackRedirects : Interceptor {
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

@UnstableApi
internal fun Throwable.hasExpiredAuthorization(): Boolean =
    generateSequence(this) { it.cause }.any {
        it is HttpDataSource.InvalidResponseCodeException && (it.responseCode == 401 || it.responseCode == 403)
    }

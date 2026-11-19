package com.xuleo.xmplus.md3.ui.screens

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.xuleo.xmplus.md3.R
import com.xuleo.xmplus.md3.core.media.PlaybackSelection
import com.xuleo.xmplus.md3.library.LibraryActions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** 收藏 for the episode on screen. [favorite] stays null until the account's list has been read. */
internal class EpisodeLibrary(
    val favorite: Boolean?,
    val available: Boolean,
    val onFavorite: () -> Unit,
)

/** The favorite state comes from the account's list. */
@Composable
internal fun rememberEpisodeLibrary(episode: PlaybackSelection?, actions: LibraryActions?): EpisodeLibrary {
    val trackId = episode?.track?.id
    val albumId = episode?.album?.id
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val failed = stringResource(R.string.library_action_failed)
    var favorite by remember(trackId) { mutableStateOf<Boolean?>(null) }
    var busy by remember(trackId) { mutableStateOf(false) }
    LaunchedEffect(trackId, actions) {
        if (trackId != null && actions != null) {
            favorite = try {
                actions.isFavorite(trackId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
        }
    }
    fun perform(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                action()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                Toast.makeText(context, failed, Toast.LENGTH_SHORT).show()
            } finally {
                busy = false
            }
        }
    }
    return EpisodeLibrary(
        favorite = favorite,
        available = trackId != null && albumId != null && actions != null && !busy,
        onFavorite = {
            val current = favorite
            if (trackId != null && albumId != null && actions != null && current != null) perform {
                actions.setFavorite(trackId, albumId, !current)
                favorite = !current
            }
        },
    )
}

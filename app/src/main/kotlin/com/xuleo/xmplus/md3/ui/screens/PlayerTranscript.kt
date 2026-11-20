package com.xuleo.xmplus.md3.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xuleo.xmplus.md3.R
import com.xuleo.xmplus.md3.core.model.XmlyException
import com.xuleo.xmplus.md3.core.protocol.XmlyTranscript
import com.xuleo.xmplus.md3.ui.components.InlineError
import kotlinx.coroutines.CancellationException

private sealed interface TranscriptState {
    data object Loading : TranscriptState
    /** [text] is null when the episode has no manuscript. */
    data class Ready(val text: String?) : TranscriptState
    data class Failed(val code: String) : TranscriptState
}

/**
 * 文稿, the player's second page: the official text of the episode, scrollable, with a retry on failure. It sits
 * straight on the player's backdrop within the same margins as the cover and controls, with no card of its own.
 */
@Composable
internal fun TranscriptPanel(trackId: String, transcript: XmlyTranscript, modifier: Modifier = Modifier) {
    var attempt by remember(trackId) { mutableIntStateOf(0) }
    val state by produceState<TranscriptState>(TranscriptState.Loading, trackId, attempt) {
        value = try {
            TranscriptState.Ready(transcript.manuscript(trackId))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: XmlyException) {
            TranscriptState.Failed(failure.code)
        } catch (_: Exception) {
            TranscriptState.Failed("unknown")
        }
    }
    val current = state
    Box(modifier) {
        when (current) {
            TranscriptState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            is TranscriptState.Failed -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                InlineError(current.code, onRetry = { attempt += 1 })
            }
            is TranscriptState.Ready -> if (current.text == null) {
                Box(Modifier.fillMaxSize().padding(horizontal = PlayerPagePadding), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.player_transcript_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Text(
                    current.text,
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                        .padding(start = PlayerPagePadding, end = PlayerPagePadding, top = 8.dp, bottom = 24.dp),
                    style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 30.sp),
                )
            }
        }
    }
}

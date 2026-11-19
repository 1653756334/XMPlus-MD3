package com.xuleo.xmplus.md3.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.xuleo.xmplus.md3.R
import com.xuleo.xmplus.md3.core.designsystem.XmLayout
import com.xuleo.xmplus.md3.core.model.AlbumSkip
import com.xuleo.xmplus.md3.core.model.XmlyException
import com.xuleo.xmplus.md3.ui.components.SheetActions
import com.xuleo.xmplus.md3.ui.components.SheetSetting
import com.xuleo.xmplus.md3.ui.components.SnapSlider
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** The common lengths, labelled under each slider. */
private val SkipMarkers = listOf(0f, 15f, 30f, 60f, 90f)
private const val SkipSliderSeconds = 180

private sealed interface Suggestion {
    data object Loading : Suggestion
    data object None : Suggestion
    data object SignIn : Suggestion
    data object Failed : Suggestion
    data class Found(val skip: AlbumSkip) : Suggestion
}

/**
 * The intro and outro skipped for every episode of the album. Each slider applies when the finger lifts. Ximalaya's
 * suggestion is offered when it differs from what is set, and never applied by itself. The footer clears the album's
 * skipping, or closes the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlayerSkipSheet(
    skip: AlbumSkip,
    durationMs: Long,
    loadSuggestion: suspend () -> AlbumSkip?,
    onSkip: (AlbumSkip) -> Unit,
    onDismiss: () -> Unit,
) {
    var intro by remember(skip) { mutableFloatStateOf(skip.introSeconds.toFloat()) }
    var outro by remember(skip) { mutableFloatStateOf(skip.outroSeconds.toFloat()) }
    var attempt by remember { mutableIntStateOf(0) }
    var suggestion by remember { mutableStateOf<Suggestion>(Suggestion.Loading) }
    val sheet = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    val scope = rememberCoroutineScope()
    val limit = maxOf(SkipSliderSeconds, skip.introSeconds, skip.outroSeconds).toFloat()

    LaunchedEffect(attempt) {
        suggestion = Suggestion.Loading
        suggestion = try {
            loadSuggestion()?.let { Suggestion.Found(it) } ?: Suggestion.None
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: XmlyException) {
            if (failure.code == "login_required") Suggestion.SignIn else Suggestion.Failed
        } catch (_: Exception) {
            Suggestion.Failed
        }
    }

    fun commit() {
        val value = AlbumSkip(intro.roundToInt(), outro.roundToInt())
        if (value != skip) onSkip(value)
    }

    fun finish() {
        scope.launch {
            sheet.hide()
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentWindowInsets = { WindowInsets.safeDrawing },
    ) {
        Column(
            Modifier.fillMaxWidth()
                .padding(start = XmLayout.PagePadding, end = XmLayout.PagePadding, bottom = XmLayout.ItemGap),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.player_skip_title),
                    Modifier.semantics { heading() },
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    stringResource(R.string.player_skip_scope),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when (val found = suggestion) {
                is Suggestion.Found -> if (!found.skip.isOff && found.skip != skip) {
                    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.secondaryContainer) {
                        Row(
                            Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, end = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                stringResource(R.string.player_skip_suggestion, found.skip.introSeconds, found.skip.outroSeconds),
                                Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                            Button(onClick = {
                                intro = found.skip.introSeconds.toFloat()
                                outro = found.skip.outroSeconds.toFloat()
                                onSkip(found.skip)
                            }) {
                                Text(stringResource(R.string.player_skip_use))
                            }
                        }
                    }
                }
                Suggestion.SignIn -> Text(
                    stringResource(R.string.player_skip_sign_in),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Suggestion.Failed -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.player_skip_suggestion_failed),
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = { attempt++ }) { Text(stringResource(R.string.action_retry)) }
                }
                Suggestion.Loading, Suggestion.None -> Unit
            }
            SkipSetting(R.string.player_skip_intro, intro, limit, { intro = it }, ::commit)
            SkipSetting(R.string.player_skip_outro, outro, limit, { outro = it }, ::commit)
            if (durationMs > 0 && (intro.roundToInt() + outro.roundToInt()) * 1_000L >= durationMs) {
                Text(
                    stringResource(R.string.player_skip_too_long),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            SheetActions(
                secondary = stringResource(R.string.player_skip_reset),
                secondaryEnabled = !skip.isOff,
                onSecondary = {
                    intro = 0f
                    outro = 0f
                    onSkip(AlbumSkip())
                },
                primary = stringResource(R.string.player_done),
                onPrimary = ::finish,
            )
        }
    }
}

@Composable
private fun SkipSetting(label: Int, seconds: Float, limit: Float, onChange: (Float) -> Unit, onFinished: () -> Unit) {
    val name = stringResource(label)
    SheetSetting(name, stringResource(R.string.player_skip_seconds, seconds.roundToInt())) {
        SnapSlider(
            value = seconds,
            onValueChange = onChange,
            onValueChangeFinished = onFinished,
            label = name,
            range = 0f..limit,
            step = 1f,
            markers = SkipMarkers,
            markerLabel = { stringResource(R.string.player_skip_seconds, it.roundToInt()) },
            magnet = 2f,
        )
    }
}

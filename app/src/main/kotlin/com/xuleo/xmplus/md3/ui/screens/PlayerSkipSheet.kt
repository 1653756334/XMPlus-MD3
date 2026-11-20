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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.xuleo.xmplus.md3.R
import com.xuleo.xmplus.md3.core.designsystem.XmLayout
import com.xuleo.xmplus.md3.core.model.AlbumSkip
import com.xuleo.xmplus.md3.core.model.MaxSkipSeconds
import com.xuleo.xmplus.md3.core.model.XmlyException
import com.xuleo.xmplus.md3.ui.components.ApplyWhenSettled
import com.xuleo.xmplus.md3.ui.components.SheetActions
import com.xuleo.xmplus.md3.ui.components.SheetHeader
import com.xuleo.xmplus.md3.ui.components.SheetSetting
import com.xuleo.xmplus.md3.ui.components.Stepper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private sealed interface Suggestion {
    data object Loading : Suggestion
    data object None : Suggestion
    data object SignIn : Suggestion
    data object Failed : Suggestion
    data class Found(val skip: AlbumSkip) : Suggestion
}

/**
 * The intro and outro skipped for every episode of the album, each on its own row with − and + in 1 s steps; holding a
 * button runs on, faster the longer it is held. A change applies once the buttons settle. Ximalaya's suggestion is
 * offered when it differs from what is set, and never applied by itself. The footer clears the album's skipping, or
 * closes the sheet.
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
    var intro by remember(skip) { mutableIntStateOf(skip.introSeconds) }
    var outro by remember(skip) { mutableIntStateOf(skip.outroSeconds) }
    var attempt by remember { mutableIntStateOf(0) }
    var suggestion by remember { mutableStateOf<Suggestion>(Suggestion.Loading) }
    val sheet = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    val scope = rememberCoroutineScope()

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

    fun set(value: AlbumSkip) {
        intro = value.introSeconds
        outro = value.outroSeconds
        if (value != skip) onSkip(value)
    }
    ApplyWhenSettled(AlbumSkip(intro, outro)) { if (it != skip) onSkip(it) }

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
            SheetHeader(stringResource(R.string.player_skip_title), stringResource(R.string.player_skip_scope))
            when (val found = suggestion) {
                is Suggestion.Found -> if (!found.skip.isOff && found.skip != AlbumSkip(intro, outro)) {
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
                            Button(onClick = { set(found.skip) }) {
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
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SkipSetting(R.string.player_skip_intro, intro) { intro = it }
                SkipSetting(R.string.player_skip_outro, outro) { outro = it }
            }
            if (durationMs > 0 && (intro + outro) * 1_000L >= durationMs) {
                Text(
                    stringResource(R.string.player_skip_too_long),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            SheetActions(
                secondary = stringResource(R.string.player_skip_reset),
                secondaryEnabled = intro != 0 || outro != 0,
                onSecondary = { set(AlbumSkip()) },
                primary = stringResource(R.string.player_done),
                onPrimary = ::finish,
            )
        }
    }
}

@Composable
private fun SkipSetting(label: Int, seconds: Int, onChange: (Int) -> Unit) {
    val name = stringResource(label)
    SheetSetting(name) {
        Stepper(
            value = seconds,
            range = 0..MaxSkipSeconds,
            onValue = onChange,
            text = stringResource(R.string.player_skip_seconds, seconds),
            name = name,
        )
    }
}
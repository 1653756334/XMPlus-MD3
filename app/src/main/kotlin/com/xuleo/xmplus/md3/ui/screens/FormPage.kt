package com.xuleo.xmplus.md3.ui.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.xuleo.xmplus.md3.ui.icons.XmIcons
import com.xuleo.xmplus.md3.R
import com.xuleo.xmplus.md3.core.designsystem.XmLayout

private val ActionHeight = 52.dp

/**
 * A full-screen form: a back arrow, then an icon, headline and one line of explanation, then the
 * fields. The headline lives in the content so it can be large; the app bar stays out of the way.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FormPage(
    icon: ImageVector,
    @StringRes title: Int,
    @StringRes detail: Int,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(title = {}, navigationIcon = {
                IconButton(onClick = onBack) { Icon(XmIcons.Outlined.ArrowBack, stringResource(R.string.action_back)) }
            })
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).imePadding(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = XmLayout.FormWidth).fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(horizontal = XmLayout.FormPadding, vertical = XmLayout.PagePadding),
                verticalArrangement = Arrangement.spacedBy(XmLayout.ItemGap + 4.dp),
            ) {
                FormHeader(icon, title, detail)
                content()
            }
        }
    }
}

@Composable
private fun FormHeader(icon: ImageVector, @StringRes title: Int, @StringRes detail: Int) {
    Column(Modifier.padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(XmLayout.ItemGap)) {
        Surface(Modifier.size(56.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        Text(stringResource(title), Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(detail), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The one filled action of a form. */
@Composable
internal fun PrimaryAction(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    Button(onClick, Modifier.fillMaxWidth().heightIn(min = ActionHeight), enabled = enabled) { Text(text) }
}

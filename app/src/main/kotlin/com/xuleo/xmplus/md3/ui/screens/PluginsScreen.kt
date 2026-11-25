package com.xuleo.xmplus.md3.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.annotation.StringRes
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xuleo.xmplus.md3.R
import com.xuleo.xmplus.md3.core.designsystem.XmLayout
import com.xuleo.xmplus.md3.core.plugin.InstallResult
import com.xuleo.xmplus.md3.core.plugin.InstalledPlugin
import com.xuleo.xmplus.md3.core.plugin.linkName
import com.xuleo.xmplus.md3.core.plugin.PluginErrorReason
import com.xuleo.xmplus.md3.core.plugin.PluginSubscription
import com.xuleo.xmplus.md3.music.InstallJob
import com.xuleo.xmplus.md3.music.PluginEvent
import com.xuleo.xmplus.md3.music.failureText
import com.xuleo.xmplus.md3.music.installSummary
import com.xuleo.xmplus.md3.music.PluginsViewModel
import com.xuleo.xmplus.md3.music.installMessage
import com.xuleo.xmplus.md3.ui.icons.XmIcons

private val PluginRowHeight = 64.dp

/** 我的 → 插件: install, update, order, enable and set up the plugins music comes from. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginsScreen(viewModel: PluginsViewModel, onBack: () -> Unit) {
    val plugins by viewModel.plugins.collectAsStateWithLifecycle()
    val subscriptions by viewModel.subscriptions.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            val text = when (event) {
                is PluginEvent.Results -> installMessage(context.resources, event.results)
                PluginEvent.LinkCopied -> context.getString(R.string.plugin_link_copied)
            }
            if (text != null) snackbar.showSnackbar(text)
        }
    }
    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) viewModel.installFiles(uris)
    }

    var sorting by rememberSaveable { mutableStateOf(false) }
    var order by remember { mutableStateOf<List<String>>(emptyList()) }
    var showInstall by rememberSaveable { mutableStateOf(false) }
    var showLink by rememberSaveable { mutableStateOf(false) }
    var showSubscriptions by rememberSaveable { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }
    var menuFor by rememberSaveable { mutableStateOf<String?>(null) }
    var variablesFor by rememberSaveable { mutableStateOf<String?>(null) }
    var alternativeFor by rememberSaveable { mutableStateOf<String?>(null) }
    var aboutFor by rememberSaveable { mutableStateOf<String?>(null) }
    var uninstallFor by rememberSaveable { mutableStateOf<String?>(null) }
    val list = plugins.orEmpty()
    fun find(platform: String?) = list.firstOrNull { it.platform == platform }
    val job = viewModel.job
    // A batch shows its progress and results in a sheet; a single plugin only the line under the bar, then a snackbar.
    val jobSheet = job != null && !viewModel.hidden && (!job.running || job.total > 1)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (sorting) R.string.plugin_sort else R.string.plugins_title)) },
                navigationIcon = {
                    IconButton(onClick = { if (sorting) sorting = false else onBack() }) {
                        Icon(if (sorting) XmIcons.Outlined.Close else XmIcons.Outlined.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (sorting) {
                        TextButton(onClick = { viewModel.reorder(order); sorting = false }) { Text(stringResource(R.string.plugin_done)) }
                    } else {
                        IconButton(onClick = { showInstall = true }, enabled = !viewModel.busy) {
                            Icon(XmIcons.Outlined.Add, stringResource(R.string.plugin_install))
                        }
                        IconButton(onClick = viewModel::updateAll, enabled = !viewModel.busy && list.any { it.info.srcUrl != null }) {
                            Icon(XmIcons.Outlined.Refresh, stringResource(R.string.plugin_update_all))
                        }
                        Box {
                            IconButton(onClick = { showMore = true }) { Icon(XmIcons.Outlined.MoreVert, stringResource(R.string.plugin_more)) }
                            DropdownMenu(showMore, onDismissRequest = { showMore = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.plugin_sort)) },
                                    enabled = list.size > 1,
                                    onClick = { showMore = false; order = list.map { it.platform }; sorting = true },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.plugin_subscriptions)) },
                                    onClick = { showMore = false; showSubscriptions = true },
                                )
                            }
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (viewModel.busy && !jobSheet) LinearProgressIndicator(Modifier.fillMaxWidth()) else Spacer(Modifier.height(4.dp))
            when {
                plugins == null -> Unit
                list.isEmpty() -> PluginsEmpty { showInstall = true }
                sorting -> SortList(order.mapNotNull(::find)) { order = it }
                else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    list.forEach { plugin ->
                        key(plugin.platform) {
                            PluginRow(plugin, onOpen = { menuFor = plugin.platform }) { viewModel.setEnabled(plugin.platform, it) }
                        }
                    }
                    Spacer(Modifier.height(XmLayout.SectionGap))
                }
            }
        }
    }

    if (jobSheet && job != null) InstallJobSheet(job, onDismiss = viewModel::dismissJob)
    if (showInstall) InstallSheet(
        onDismiss = { showInstall = false },
        onFile = { showInstall = false; pickFiles.launch(arrayOf("*/*")) },
        onLink = { showInstall = false; showLink = true },
    )
    if (showLink) LinkDialog(R.string.plugin_from_link, R.string.plugin_install, { showLink = false }) {
        showLink = false
        viewModel.installLink(it)
    }
    find(menuFor)?.let { plugin ->
        PluginMenu(
            plugin,
            onDismiss = { menuFor = null },
            onSettings = { menuFor = null; variablesFor = plugin.platform },
            onAlternative = { menuFor = null; alternativeFor = plugin.platform },
            onUpdate = { menuFor = null; viewModel.update(plugin.platform) },
            onAbout = { menuFor = null; aboutFor = plugin.platform },
            onCopy = {
                menuFor = null
                plugin.info.srcUrl?.let { url ->
                    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(plugin.platform, url))
                    viewModel.linkCopied()
                }
            },
            onUninstall = { menuFor = null; uninstallFor = plugin.platform },
            busy = viewModel.busy,
        )
    }
    find(variablesFor)?.let { plugin ->
        VariablesDialog(plugin, { variablesFor = null }) { values ->
            variablesFor = null
            viewModel.setVariables(plugin.platform, values)
        }
    }
    find(alternativeFor)?.let { plugin ->
        AlternativeSheet(plugin, list.filter { it.platform != plugin.platform && it.info.usable && "getMediaSource" in it.info.methods },
            onDismiss = { alternativeFor = null }) {
            alternativeFor = null
            viewModel.setAlternative(plugin.platform, it)
        }
    }
    find(aboutFor)?.let { plugin ->
        AlertDialog(
            onDismissRequest = { aboutFor = null },
            title = { Text(plugin.platform) },
            text = { Text(plugin.info.description.orEmpty()) },
            confirmButton = { TextButton(onClick = { aboutFor = null }) { Text(stringResource(R.string.action_close)) } },
        )
    }
    find(uninstallFor)?.let { plugin ->
        AlertDialog(
            onDismissRequest = { uninstallFor = null },
            title = { Text(stringResource(R.string.plugin_uninstall_title, plugin.platform)) },
            confirmButton = {
                TextButton(onClick = { uninstallFor = null; viewModel.uninstall(plugin.platform) }) {
                    Text(stringResource(R.string.plugin_uninstall), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { uninstallFor = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    if (showSubscriptions) SubscriptionsSheet(
        subscriptions, busy = viewModel.busy,
        onDismiss = { showSubscriptions = false },
        onChange = viewModel::setSubscriptions,
        onUpdate = { showSubscriptions = false; viewModel.updateSubscriptions() },
    )
}

@Composable
private fun PluginsEmpty(onInstall: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(stringResource(R.string.plugins_empty), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onInstall) { Text(stringResource(R.string.plugin_install)) }
        Spacer(Modifier.height(48.dp))
    }
}

@Composable
private fun PluginRow(plugin: InstalledPlugin, onOpen: () -> Unit, onToggle: (Boolean) -> Unit) {
    val error = plugin.info.error ?: if (plugin.info.usable) null else PluginErrorReason.CannotParse
    ListItem(
        onClick = onOpen,
        modifier = Modifier.heightIn(min = PluginRowHeight),
        supportingContent = {
            if (error != null) Text(stringResource(errorText(error)), color = MaterialTheme.colorScheme.error, maxLines = 1)
            else pluginMeta(plugin)?.let { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        },
        trailingContent = if (error == null) ({ Switch(checked = !plugin.meta.disabled, onCheckedChange = onToggle) }) else null,
    ) { Text(plugin.platform, maxLines = 1, overflow = TextOverflow.Ellipsis) }
}

/** Version and author, the one quiet line under a plugin's name. */
@Composable
private fun pluginMeta(plugin: InstalledPlugin): String? = listOfNotNull(
    plugin.info.version?.takeIf { it.isNotBlank() }?.let { stringResource(R.string.plugin_version, it) },
    plugin.info.author?.takeIf { it.isNotBlank() },
).joinToString(" · ").ifEmpty { null }

@StringRes
private fun errorText(reason: PluginErrorReason) = when (reason) {
    PluginErrorReason.CannotParse -> R.string.plugin_error_parse
    PluginErrorReason.VersionNotMatch -> R.string.plugin_error_version
}

/** Rows that move under the finger once their handle is dragged; the new order is kept until 完成. */
@Composable
private fun SortList(plugins: List<InstalledPlugin>, onOrder: (List<String>) -> Unit) {
    val rowPx = with(LocalDensity.current) { PluginRowHeight.toPx() }
    val haptics = LocalHapticFeedback.current
    var dragging by remember { mutableStateOf<String?>(null) }
    var offset by remember { mutableFloatStateOf(0f) }
    val current by rememberUpdatedState(plugins)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        plugins.forEach { plugin ->
            key(plugin.platform) {
                val active = dragging == plugin.platform
                Surface(
                    color = if (active) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surface,
                    modifier = Modifier.zIndex(if (active) 1f else 0f)
                        .graphicsLayer { translationY = if (active) offset else 0f }
                        .then(if (active) Modifier.shadow(6.dp) else Modifier),
                ) {
                    Row(Modifier.fillMaxWidth().height(PluginRowHeight).padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(plugin.platform, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            pluginMeta(plugin)?.let {
                                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                        }
                        Box(
                            Modifier.size(48.dp).pointerInput(plugin.platform) {
                                detectVerticalDragGestures(
                                    onDragStart = { dragging = plugin.platform; offset = 0f; haptics.performHapticFeedback(HapticFeedbackType.LongPress) },
                                    onDragEnd = { dragging = null; offset = 0f },
                                    onDragCancel = { dragging = null; offset = 0f },
                                ) { change, amount ->
                                    change.consume()
                                    offset += amount
                                    val names = current.map { it.platform }
                                    val at = names.indexOf(plugin.platform)
                                    val target = when {
                                        offset > rowPx / 2 && at < names.lastIndex -> at + 1
                                        offset < -rowPx / 2 && at > 0 -> at - 1
                                        else -> at
                                    }
                                    if (target != at) {
                                        offset -= (target - at) * rowPx
                                        onOrder(names.toMutableList().apply { add(target, removeAt(at)) })
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                }
                            },
                            contentAlignment = Alignment.Center,
                        ) { Icon(XmIcons.Outlined.SwapVert, stringResource(R.string.plugin_sort)) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InstallSheet(onDismiss: () -> Unit, onFile: () -> Unit, onLink: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        SheetAction(R.string.plugin_from_file, XmIcons.Outlined.FileOpen, onFile)
        SheetAction(R.string.plugin_from_link, XmIcons.Outlined.Language, onLink)
        Spacer(Modifier.height(16.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PluginMenu(
    plugin: InstalledPlugin,
    onDismiss: () -> Unit,
    onSettings: () -> Unit,
    onAlternative: () -> Unit,
    onUpdate: () -> Unit,
    onAbout: () -> Unit,
    onCopy: () -> Unit,
    onUninstall: () -> Unit,
    busy: Boolean,
) {
    val usable = plugin.info.usable
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 4.dp)) {
            Text(plugin.platform, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            pluginMeta(plugin)?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Spacer(Modifier.height(8.dp))
        if (usable && plugin.info.variables.isNotEmpty()) SheetAction(R.string.plugin_settings, XmIcons.Outlined.Tune, onSettings)
        if (usable && "getMediaSource" in plugin.info.methods) {
            SheetAction(R.string.plugin_alternative, XmIcons.Outlined.SwapVert, onAlternative)
        }
        if (plugin.info.srcUrl != null) {
            SheetAction(R.string.plugin_update, XmIcons.Outlined.Refresh, onUpdate, enabled = !busy)
            SheetAction(R.string.plugin_copy_link, XmIcons.Outlined.Language, onCopy)
        }
        if (!plugin.info.description.isNullOrBlank()) SheetAction(R.string.plugin_about, XmIcons.Outlined.AutoAwesome, onAbout)
        SheetAction(R.string.plugin_uninstall, XmIcons.Outlined.DeleteOutline, onUninstall)
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun SheetAction(@StringRes label: Int, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit, enabled: Boolean = true) {
    ListItem(
        onClick = onClick,
        enabled = enabled,
        leadingContent = { Icon(icon, contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
    ) { Text(stringResource(label)) }
}

/** Progress of a batch with a bar and the plugin it is at, then what each one came to, failures first. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InstallJobSheet(job: InstallJob, onDismiss: () -> Unit) {
    val running by rememberUpdatedState(job.running)
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { it != SheetValue.Hidden || !running })
    val resources = LocalContext.current.resources
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        val results = job.results
        if (results == null) {
            Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 32.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(if (job.update) R.string.plugin_updating else R.string.plugin_installing),
                        style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f),
                    )
                    Text(
                        stringResource(R.string.plugin_progress, (job.done + 1).coerceAtMost(job.total), job.total),
                        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(16.dp))
                val progress by animateFloatAsState(
                    job.done.toFloat() / job.total.coerceAtLeast(1), MaterialTheme.motionScheme.defaultEffectsSpec(), label = "installProgress",
                )
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                Text(
                    job.current?.let(::linkName).orEmpty(), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        } else {
            Text(
                installSummary(resources, results), style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            LazyColumn(Modifier.weight(1f, fill = false)) {
                items(results.sortedBy { it !is InstallResult.Failed }) { InstallResultRow(it, job.update) }
            }
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)) {
                Text(stringResource(R.string.plugin_done))
            }
        }
    }
}

@Composable
private fun InstallResultRow(result: InstallResult, update: Boolean) {
    val failed = result is InstallResult.Failed
    val name = when (result) {
        is InstallResult.Installed -> result.platform
        is InstallResult.AlreadyInstalled -> result.platform
        is InstallResult.Failed -> linkName(result.source)
    }
    val detail = when (result) {
        is InstallResult.Installed -> result.version?.let { stringResource(R.string.plugin_version, it) }
        is InstallResult.AlreadyInstalled -> stringResource(if (update) R.string.plugin_result_latest else R.string.plugin_result_already)
        is InstallResult.Failed -> stringResource(failureText(result.reason))
    }
    ListItem(
        headlineContent = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = detail?.let { { Text(it, color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) } },
        leadingContent = if (failed) ({ Icon(XmIcons.Outlined.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error) }) else null,
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
    )
}

/** Starts with the link on the clipboard, if there is one. */
@Composable
private fun LinkDialog(@StringRes title: Int, @StringRes confirm: Int, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val context = LocalContext.current
    var text by rememberSaveable {
        val clip = try {
            context.getSystemService(ClipboardManager::class.java)?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
        } catch (_: Exception) {
            null
        }
        mutableStateOf(clip?.trim()?.takeIf { it.startsWith("http://") || it.startsWith("https://") }.orEmpty())
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = {
            OutlinedTextField(
                text, { text = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.plugin_link_hint)) },
                trailingIcon = if (text.isNotEmpty()) ({
                    IconButton(onClick = { text = "" }) { Icon(XmIcons.Outlined.Cancel, stringResource(R.string.action_clear)) }
                }) else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text.trim()) }, enabled = text.isNotBlank()) { Text(stringResource(confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** One field per user variable the plugin declares, filled with what was saved. */
@Composable
private fun VariablesDialog(plugin: InstalledPlugin, onDismiss: () -> Unit, onSave: (Map<String, String>) -> Unit) {
    val values = remember(plugin.platform) {
        mutableStateMapOf<String, String>().apply { plugin.info.variables.forEach { put(it.key, plugin.meta.variables[it.key].orEmpty()) } }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.plugin_settings)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                plugin.info.variables.forEach { variable ->
                    OutlinedTextField(
                        values[variable.key].orEmpty(), { values[variable.key] = it },
                        label = { Text(variable.name?.takeIf { it.isNotBlank() } ?: variable.key) },
                        placeholder = variable.hint?.takeIf { it.isNotBlank() }?.let { { Text(it) } },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(values.toMap().filterValues { it.isNotEmpty() }) }) { Text(stringResource(R.string.plugin_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlternativeSheet(plugin: InstalledPlugin, candidates: List<InstalledPlugin>, onDismiss: () -> Unit, onPick: (String?) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(stringResource(R.string.plugin_alternative), style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        Column(Modifier.selectableGroup()) {
            (listOf<String?>(null) + candidates.map { it.platform }).forEach { name ->
                val selected = plugin.meta.alternative == name
                Row(
                    Modifier.fillMaxWidth().height(56.dp).selectable(selected, role = Role.RadioButton) { onPick(name) }.padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(name ?: stringResource(R.string.plugin_alternative_none), Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    RadioButton(selected = selected, onClick = null)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubscriptionsSheet(
    subscriptions: List<PluginSubscription>,
    busy: Boolean,
    onDismiss: () -> Unit,
    onChange: (List<PluginSubscription>) -> Unit,
    onUpdate: () -> Unit,
) {
    var adding by rememberSaveable { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.plugin_subscriptions), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = { adding = true }) { Icon(XmIcons.Outlined.Add, stringResource(R.string.subscription_add)) }
        }
        subscriptions.forEach { subscription ->
            ListItem(
                headlineContent = { Text(subscription.name.ifBlank { subscription.url }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = if (subscription.name.isNotBlank()) ({ Text(subscription.url, maxLines = 1, overflow = TextOverflow.Ellipsis) }) else null,
                trailingContent = {
                    IconButton(onClick = { onChange(subscriptions - subscription) }) {
                        Icon(XmIcons.Outlined.DeleteOutline, stringResource(R.string.plugin_uninstall))
                    }
                },
                colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
            )
        }
        Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)) {
            if (subscriptions.isEmpty()) Text(stringResource(R.string.subscription_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            else Button(onClick = onUpdate, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.subscription_update)) }
        }
    }
    if (adding) SubscriptionDialog({ adding = false }) { added ->
        adding = false
        onChange(subscriptions + added)
    }
}

@Composable
private fun SubscriptionDialog(onDismiss: () -> Unit, onAdd: (PluginSubscription) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.subscription_add)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.subscription_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(url, { url = it }, label = { Text(stringResource(R.string.plugin_link_hint)) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(PluginSubscription(name.trim(), url.trim())) }, enabled = url.isNotBlank()) {
                Text(stringResource(R.string.plugin_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}


package com.xuleo.xmplus.md3.ui.screens

import android.content.ContentResolver
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.xuleo.xmplus.md3.core.account.XmplusArchive
import com.xuleo.xmplus.md3.core.model.XmlyException
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val ArchiveTypes = arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")

/** Opens the system file picker; [onPicked] gets the chosen backup, to be read with [readArchive]. */
@Composable
internal fun rememberArchivePicker(onPicked: (Uri) -> Unit): () -> Unit {
    val latest = rememberUpdatedState(onPicked)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(latest.value) }
    return { launcher.launch(ArchiveTypes) }
}

/** Reads a backup, stopping at the size limit instead of loading an arbitrarily large file. */
internal suspend fun ContentResolver.readArchive(uri: Uri): ByteArray = withContext(Dispatchers.IO) {
    val input = openInputStream(uri) ?: throw XmlyException("archive_invalid", definitive = true)
    input.use {
        val content = ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val read = it.read(buffer)
            if (read < 0) break
            total += read
            if (total > XmplusArchive.MaxBytes) throw XmlyException("archive_invalid", definitive = true)
            content.write(buffer, 0, read)
        }
        content.toByteArray()
    }
}

/** The one place that turns a picked backup into a preview; both screens that offer the import use it. */
@Composable
internal fun rememberArchiveImport(preview: (suspend () -> ByteArray) -> Unit): () -> Unit {
    val resolver = LocalContext.current.contentResolver
    return rememberArchivePicker { uri -> preview { resolver.readArchive(uri) } }
}
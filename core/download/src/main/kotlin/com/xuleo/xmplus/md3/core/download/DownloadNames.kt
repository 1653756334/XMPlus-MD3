package com.xuleo.xmplus.md3.core.download

/** File and folder names for downloads, from the default pattern: DL-10 to DL-13. */
internal object DownloadNames {
    private const val MaxNameBytes = 255
    private const val MaxFolderBytes = 120
    private val Illegal = Regex("[<>:\"/\\\\|?*\\x00-\\x1f\\x7f]")
    private val Reserved = Regex("^(?:CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(?:\\..*)?$", RegexOption.IGNORE_CASE)

    /** Episode 7 of 120 is `007`; the width follows the album's total and is at least three digits. */
    fun index(index: Int, albumTotal: Int): String = index.toString().padStart(maxOf(3, albumTotal.toString().length), '0')

    /** `{index} - {title} [{trackId}]` with an extension. The title is shortened to fit; the id is always kept. */
    fun episode(index: String, title: String, trackId: String, extension: String): String {
        val head = "$index - "
        val tail = " [$trackId].$extension"
        val room = MaxNameBytes - bytes(head) - bytes(tail)
        return head + cut(clean(title).ifEmpty { trackId }, room) + tail
    }

    /** `{album title} [{albumId}]`, the folder that holds one album's episodes. */
    fun album(title: String, albumId: String): String {
        val tail = " [$albumId]"
        return cut(clean(title).ifEmpty { "album" }, MaxFolderBytes - bytes(tail)) + tail
    }

    /**
     * `{artist} - {title} [{songId}]` with an extension, the file of one song. The id keeps two songs of the same name
     * apart, also across plugins, the way an episode's own id does.
     */
    fun song(artist: String, title: String, songId: String, extension: String): String {
        val tail = " [${clean(songId)}].$extension"
        val head = clean(artist).ifEmpty { "song" } + " - "
        val room = MaxNameBytes - bytes(head) - bytes(tail)
        return head + cut(clean(title).ifEmpty { songId }, room) + tail
    }

    /** The folder songs go to, next to the albums. */
    const val SongsFolder = "音乐"

    private fun clean(text: String): String {
        val safe = text.replace(Illegal, "_").trim().trimEnd('.', ' ')
        return if (Reserved.matches(safe)) "_$safe" else safe
    }

    private fun cut(text: String, maxBytes: Int): String {
        val out = StringBuilder()
        var used = 0
        var offset = 0
        while (offset < text.length) {
            val codePoint = text.codePointAt(offset)
            val size = bytes(String(Character.toChars(codePoint)))
            if (used + size > maxBytes) break
            out.appendCodePoint(codePoint)
            used += size
            offset += Character.charCount(codePoint)
        }
        return out.toString().trimEnd('.', ' ')
    }

    private fun bytes(text: String) = text.toByteArray(Charsets.UTF_8).size
}
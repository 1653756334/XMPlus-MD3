package com.xuleo.xmplus.md3.core.download

import java.io.File
import java.util.logging.Level
import java.util.logging.Logger
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.images.ArtworkFactory

/**
 * Writes what a song's file should carry: title, artist, album, cover and the lyric, for MP3 (ID3v2), FLAC (Vorbis
 * comments) and M4A (MP4 atoms). Only the tags are written, never the sound, and only for a format the tagger knows:
 * a file it cannot open keeps playing without them.
 */
internal object SongTags {
    private val log = Logger.getLogger("org.jaudiotagger")

    init {
        // The tagger is talkative; a download log is not the place for it.
        log.level = Level.OFF
    }

    fun write(file: File, title: String, artist: String, album: String, cover: ByteArray?, lyric: String?) {
        if (!file.isFile || file.length() == 0L) return
        val audio = try {
            AudioFileIO.read(file)
        } catch (_: Exception) {
            return
        }
        val tag = try {
            audio.tagOrCreateAndSetDefault
        } catch (_: Exception) {
            null
        } ?: return
        set(tag, FieldKey.TITLE, title)
        set(tag, FieldKey.ARTIST, artist)
        set(tag, FieldKey.ALBUM, album)
        set(tag, FieldKey.LYRICS, lyric)
        cover?.let { put(tag, it) }
        try {
            audio.commit()
        } catch (_: Exception) {
            // Half-written tags are worse than none: the next attempt starts over.
        }
        if (cover != null) FlacCover.write(file, cover)
    }

    /** A field a format cannot hold is skipped; the others are still written. */
    private fun set(tag: Tag, key: FieldKey, value: String?) {
        if (value.isNullOrBlank()) return
        try {
            tag.setField(key, value)
        } catch (_: Exception) {
            // No such field in this format.
        }
    }

    private fun put(tag: Tag, bytes: ByteArray) {
        if (bytes.size > MaxCoverBytes) return
        try {
            val artwork = ArtworkFactory.getNew()
            artwork.binaryData = bytes
            artwork.mimeType = imageType(bytes)
            artwork.description = "Cover"
            artwork.pictureType = FrontCover
            tag.deleteArtworkField()
            tag.addField(artwork)
        } catch (_: Exception) {
            // Covers are optional; some formats refuse them.
        }
    }

    private fun imageType(bytes: ByteArray): String = when {
        bytes.size > 8 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() -> "image/jpeg"
        bytes.size > 8 && bytes[1] == 0x50.toByte() && bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte() -> "image/png"
        bytes.size > 12 && bytes[8] == 0x57.toByte() && bytes[9] == 0x45.toByte() && bytes[10] == 0x42.toByte() -> "image/webp"
        else -> "image/jpeg"
    }

    private const val MaxCoverBytes = 8 * 1024 * 1024

    /** ID3's picture type for the cover on the front of the sleeve. */
    private const val FrontCover = 3
}

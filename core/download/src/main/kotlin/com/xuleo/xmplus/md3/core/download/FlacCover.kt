package com.xuleo.xmplus.md3.core.download

import java.io.File

/**
 * Puts a cover into a FLAC file. jaudiotagger reads FLAC pictures but writes none, so the block is added by hand: the
 * metadata blocks are walked, any picture is dropped, the new one goes last with the last-block flag, and the audio
 * frames are copied unchanged. A file that does not look like FLAC is left alone.
 */
internal object FlacCover {
    fun write(file: File, image: ByteArray) {
        val text = try {
            file.readBytes()
        } catch (_: Exception) {
            return
        }
        if (text.size < 8 || text[0] != 'f'.code.toByte() || text[1] != 'L'.code.toByte() ||
            text[2] != 'a'.code.toByte() || text[3] != 'C'.code.toByte()
        ) return

        val blocks = ArrayList<ByteArray>()
        var offset = 4
        var audio = -1
        while (offset + 4 <= text.size) {
            val first = text[offset].toInt() and 0xFF
            val last = first and 0x80 != 0
            val type = first and 0x7F
            val end = offset + 4 + length(text, offset)
            if (end > text.size) return
            // Old pictures are replaced by the new one; the frames start after the last metadata block.
            if (type != Picture) blocks += text.copyOfRange(offset, end)
            if (last) {
                audio = end
                break
            }
            offset = end
        }
        if (audio < 0) return
        val block = picture(image)
        val out = java.io.ByteArrayOutputStream(text.size + block.size)
        out.write(text, 0, 4)
        blocks.forEach { old ->
            // Only the block written last may carry the last-block flag.
            out.write(old[0].toInt() and 0x7F)
            out.write(old, 1, old.size - 1)
        }
        out.write(Picture or 0x80)
        out.write(block.size ushr 16)
        out.write(block.size ushr 8)
        out.write(block.size)
        out.write(block)
        out.write(text, audio, text.size - audio)
        try {
            file.writeBytes(out.toByteArray())
        } catch (_: Exception) {
            // The song keeps playing; the cover is lost.
        }
    }

    private fun length(text: ByteArray, offset: Int): Int =
        ((text[offset + 1].toInt() and 0xFF) shl 16) or ((text[offset + 2].toInt() and 0xFF) shl 8) or
            (text[offset + 3].toInt() and 0xFF)

    /** A FLAC picture block: front cover, no description, the frame size when the image tells it. */
    private fun picture(image: ByteArray): ByteArray {
        val (width, height) = size(image)
        val mime = mime(image).toByteArray(Charsets.US_ASCII)
        val out = java.io.ByteArrayOutputStream(image.size + mime.size + 64)
        fun int(value: Int) {
            out.write(value ushr 24)
            out.write(value ushr 16)
            out.write(value ushr 8)
            out.write(value)
        }
        int(3) // Front cover
        int(mime.size)
        out.write(mime, 0, mime.size)
        int(0) // No description
        int(width)
        int(height)
        int(24) // Bits per pixel, a guess players rarely read
        int(0) // Not an indexed image
        int(image.size)
        out.write(image, 0, image.size)
        return out.toByteArray()
    }

    private fun mime(image: ByteArray): String = when {
        image.size > 8 && image[0] == 0xFF.toByte() && image[1] == 0xD8.toByte() -> "image/jpeg"
        image.size > 8 && image[1] == 0x50.toByte() && image[2] == 0x4E.toByte() -> "image/png"
        image.size > 12 && image[8] == 0x57.toByte() && image[9] == 0x45.toByte() -> "image/webp"
        else -> "image/jpeg"
    }

    /** The image's own size, or zeroes when it is not a JPEG or PNG this can read. */
    private fun size(image: ByteArray): Pair<Int, Int> {
        if (image.size > 24 && image[1] == 0x50.toByte() && image[2] == 0x4E.toByte()) {
            return pair(image, 16) to pair(image, 20)
        }
        if (image.size > 4 && image[0] == 0xFF.toByte() && image[1] == 0xD8.toByte()) {
            var at = 2
            while (at + 9 < image.size) {
                if (image[at] != 0xFF.toByte()) {
                    at++
                    continue
                }
                val marker = image[at + 1].toInt() and 0xFF
                val start = marker in 0xC0..0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC
                if (start) return pair(image, at + 7) to pair(image, at + 5)
                if (marker == 0xD8 || marker == 0xD9) {
                    at += 2
                    continue
                }
                at += 2 + pair(image, at + 2)
            }
        }
        return 0 to 0
    }

    private fun pair(image: ByteArray, at: Int): Int =
        if (at + 1 < image.size) ((image[at].toInt() and 0xFF) shl 8) or (image[at + 1].toInt() and 0xFF) else 0

    private const val Picture = 6
}

package com.xuleo.xmplus.md3.core.download

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Puts a cover into a FLAC file. jaudiotagger reads FLAC pictures but writes none, so the block is added by hand: the
 * metadata blocks are walked, any picture is dropped, the new one goes last with the last-block flag, and the audio
 * frames are copied unchanged. A file that does not look like FLAC is left alone.
 *
 * The file is streamed into a sibling and moved over the original: a whole song does not fit in memory.
 */
internal object FlacCover {
    fun write(file: File, image: ByteArray) {
        val temp = File(file.parentFile, "${file.name}.cover")
        try {
            write(file, temp, image)
        } catch (_: Throwable) {
            // The song keeps playing; the cover is lost.
        } finally {
            temp.delete()
        }
    }

    private fun write(file: File, temp: File, image: ByteArray) {
        RandomAccessFile(file, "r").use { input ->
            val magic = ByteArray(4)
            if (input.read(magic) != 4 || magic[0] != 'f'.code.toByte() || magic[1] != 'L'.code.toByte() ||
                magic[2] != 'a'.code.toByte() || magic[3] != 'C'.code.toByte()
            ) return

            // Walk the metadata, keeping where each block that is not a picture sits; the frames follow the last one.
            val blocks = ArrayList<LongArray>()
            var offset = 4L
            var audio = -1L
            while (offset + 4 <= input.length()) {
                val header = ByteArray(4)
                input.seek(offset)
                if (input.read(header) != 4) return
                val first = header[0].toInt() and 0xFF
                val length = ((header[1].toInt() and 0xFF) shl 16) or ((header[2].toInt() and 0xFF) shl 8) or
                    (header[3].toInt() and 0xFF)
                val end = offset + 4 + length
                if (end > input.length()) return
                // Old pictures are replaced by the new one.
                if (first and 0x7F != Picture) blocks += longArrayOf(offset, length.toLong())
                if (first and 0x80 != 0) {
                    audio = end
                    break
                }
                offset = end
            }
            if (audio < 0) return

            val block = picture(image)
            BufferedOutputStream(FileOutputStream(temp), Buffer).use { out ->
                out.write(magic)
                blocks.forEach { old ->
                    input.seek(old[0])
                    val header = ByteArray(4)
                    input.readFully(header)
                    // Only the block written last may carry the last-block flag.
                    out.write(header[0].toInt() and 0x7F)
                    out.write(header, 1, 3)
                    copy(input, out, old[1])
                }
                out.write(Picture or 0x80)
                out.write(block.size ushr 16)
                out.write(block.size ushr 8)
                out.write(block.size)
                out.write(block)
                input.seek(audio)
                copy(input, out, input.length() - audio)
            }
        }
        Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    /** Copies a byte count in chunks, never the whole file. */
    private fun copy(input: RandomAccessFile, out: OutputStream, bytes: Long) {
        val buffer = ByteArray(Buffer)
        var left = bytes
        while (left > 0) {
            val read = input.read(buffer, 0, minOf(left, buffer.size.toLong()).toInt())
            if (read < 0) return
            out.write(buffer, 0, read)
            left -= read
        }
    }

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

    private const val Buffer = 64 * 1024
}

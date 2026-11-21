package org.nationalsounds.app.tts

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Joins the WAV files produced by Android's TextToSpeech.synthesizeToFile() into a single
 * 16-bit PCM WAV file, inserting digital silence between them.
 */
object WavMerger {

    sealed interface Part {
        data class Audio(val file: File) : Part
        data class Silence(val millis: Int) : Part
    }

    data class WavInfo(
        val sampleRate: Int,
        val channels: Int,
        val bitsPerSample: Int,
        val dataOffset: Long,
        val dataLength: Long,
    )

    fun readInfo(file: File): WavInfo = RandomAccessFile(file, "r").use { raf ->
        val fileLength = raf.length()
        require(fileLength >= 12) { "ملف WAV غير صالح" }

        val riff = ByteArray(12)
        raf.readFully(riff)
        require(
            String(riff, 0, 4, Charsets.US_ASCII) == "RIFF" &&
                String(riff, 8, 4, Charsets.US_ASCII) == "WAVE"
        ) { "ملف WAV غير صالح" }

        var sampleRate = 0
        var channels = 0
        var bits = 0
        var pos = 12L
        val chunkHeader = ByteArray(8)

        while (pos + 8 <= fileLength) {
            raf.seek(pos)
            raf.readFully(chunkHeader)
            val id = String(chunkHeader, 0, 4, Charsets.US_ASCII)
            val size = ByteBuffer.wrap(chunkHeader, 4, 4)
                .order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL
            val body = pos + 8

            if (id == "fmt ") {
                val fmt = ByteArray(16)
                raf.readFully(fmt)
                val bb = ByteBuffer.wrap(fmt).order(ByteOrder.LITTLE_ENDIAN)
                bb.short // format tag
                channels = bb.short.toInt()
                sampleRate = bb.int
                bb.int // byte rate
                bb.short // block align
                bits = bb.short.toInt()
            } else if (id == "data") {
                val remaining = fileLength - body
                // Some engines write 0 or 0xFFFFFFFF as the size; trust the file length then.
                val length = if (size == 0L || size > remaining) remaining else size
                require(sampleRate > 0 && channels > 0 && bits > 0) { "ترويسة WAV ناقصة" }
                return@use WavInfo(sampleRate, channels, bits, body, length)
            }
            pos = body + size + (size and 1L)
        }
        throw IllegalArgumentException("لم يُعثر على بيانات الصوت في الملف")
    }

    fun durationMillis(file: File): Long {
        val info = readInfo(file)
        val bytesPerSecond = info.sampleRate.toLong() * info.channels * info.bitsPerSample / 8
        return info.dataLength * 1000 / bytesPerSecond
    }

    /** Merges [parts] into [output] and returns the duration of the result in milliseconds. */
    fun merge(parts: List<Part>, output: File): Long {
        val firstAudio = parts.filterIsInstance<Part.Audio>().firstOrNull()
        requireNotNull(firstAudio) { "لا توجد مقاطع صوتية للدمج" }

        val first = readInfo(firstAudio.file)
        require(first.bitsPerSample == 16) { "صيغة الصوت غير مدعومة (يلزم 16 بت)" }
        val blockAlign = first.channels * first.bitsPerSample / 8
        val bytesPerSecond = first.sampleRate.toLong() * blockAlign

        var totalData = 0L
        val copyBuffer = ByteArray(1 shl 16)
        val zeros = ByteArray(1 shl 16)

        BufferedOutputStream(FileOutputStream(output), 1 shl 16).use { out ->
            out.write(ByteArray(HEADER_SIZE)) // placeholder, patched below

            for (part in parts) {
                when (part) {
                    is Part.Audio -> {
                        val info = readInfo(part.file)
                        require(
                            info.sampleRate == first.sampleRate &&
                                info.channels == first.channels &&
                                info.bitsPerSample == first.bitsPerSample
                        ) { "اختلاف في صيغة المقاطع الصوتية" }

                        RandomAccessFile(part.file, "r").use { raf ->
                            raf.seek(info.dataOffset)
                            var left = info.dataLength - info.dataLength % blockAlign
                            while (left > 0) {
                                val want = minOf(copyBuffer.size.toLong(), left).toInt()
                                val n = raf.read(copyBuffer, 0, want)
                                if (n <= 0) break
                                out.write(copyBuffer, 0, n)
                                left -= n
                                totalData += n
                            }
                        }
                    }

                    is Part.Silence -> {
                        var bytes = bytesPerSecond * part.millis / 1000
                        bytes -= bytes % blockAlign
                        while (bytes > 0) {
                            val n = minOf(zeros.size.toLong(), bytes).toInt()
                            out.write(zeros, 0, n)
                            bytes -= n
                            totalData += n
                        }
                    }
                }
            }
        }

        RandomAccessFile(output, "rw").use { raf ->
            raf.seek(0)
            raf.write(header(first.sampleRate, first.channels, first.bitsPerSample, totalData))
        }
        return totalData * 1000 / bytesPerSecond
    }

    private const val HEADER_SIZE = 44

    private fun header(sampleRate: Int, channels: Int, bits: Int, dataLength: Long): ByteArray {
        val blockAlign = channels * bits / 8
        val bb = ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        bb.put("RIFF".toByteArray(Charsets.US_ASCII))
        bb.putInt((36 + dataLength).toInt())
        bb.put("WAVE".toByteArray(Charsets.US_ASCII))
        bb.put("fmt ".toByteArray(Charsets.US_ASCII))
        bb.putInt(16)
        bb.putShort(1) // PCM
        bb.putShort(channels.toShort())
        bb.putInt(sampleRate)
        bb.putInt(sampleRate * blockAlign)
        bb.putShort(blockAlign.toShort())
        bb.putShort(bits.toShort())
        bb.put("data".toByteArray(Charsets.US_ASCII))
        bb.putInt(dataLength.toInt())
        return bb.array()
    }
}

package org.nationalsounds.app.tts

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WavMergerTest {

    private fun wav(samples: Int, rate: Int = 8000): File {
        val data = ByteArray(samples * 2) { 1 }
        val bb = ByteBuffer.allocate(44 + data.size).order(ByteOrder.LITTLE_ENDIAN)
        bb.put("RIFF".toByteArray())
        bb.putInt(36 + data.size)
        bb.put("WAVE".toByteArray())
        bb.put("fmt ".toByteArray())
        bb.putInt(16)
        bb.putShort(1)
        bb.putShort(1)
        bb.putInt(rate)
        bb.putInt(rate * 2)
        bb.putShort(2)
        bb.putShort(16)
        bb.put("data".toByteArray())
        bb.putInt(data.size)
        bb.put(data)
        return File.createTempFile("ns_test", ".wav").apply {
            deleteOnExit()
            writeBytes(bb.array())
        }
    }

    @Test
    fun mergesAudioAndSilence() {
        val a = wav(800) // 100 ms at 8 kHz
        val b = wav(800)
        val out = File.createTempFile("ns_out", ".wav").apply { deleteOnExit() }

        val millis = WavMerger.merge(
            listOf(
                WavMerger.Part.Audio(a),
                WavMerger.Part.Silence(100),
                WavMerger.Part.Audio(b),
            ),
            out,
        )

        assertEquals(300L, millis)
        assertEquals(44L + 4800L, out.length())
        assertEquals(300L, WavMerger.durationMillis(out))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsMismatchedSampleRates() {
        val out = File.createTempFile("ns_out", ".wav").apply { deleteOnExit() }
        WavMerger.merge(
            listOf(WavMerger.Part.Audio(wav(800, 8000)), WavMerger.Part.Audio(wav(800, 16000))),
            out,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsEmptyInput() {
        val out = File.createTempFile("ns_out", ".wav").apply { deleteOnExit() }
        WavMerger.merge(listOf(WavMerger.Part.Silence(100)), out)
    }
}

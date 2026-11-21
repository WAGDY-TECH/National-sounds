package org.nationalsounds.app.tts

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

/** One Arabic voice installed on the device. */
data class ArabicVoice(
    val id: String,
    val label: String,
    val details: String,
)

/**
 * Thin wrapper around Android's [TextToSpeech] that:
 *  - exposes the Arabic voices installed on the device,
 *  - renders text to WAV files (suspending, cancellable),
 *  - plays short live previews.
 */
class TtsEngine(context: Context) {

    enum class Status { INITIALIZING, READY, NO_ARABIC, FAILED }

    private val appContext = context.applicationContext
    private val pending = ConcurrentHashMap<String, CancellableContinuation<Boolean>>()
    private var tts: TextToSpeech? = null
    private var voicesByName: Map<String, Voice> = emptyMap()

    private val _status = MutableStateFlow(Status.INITIALIZING)
    val status: StateFlow<Status> = _status

    private val _voices = MutableStateFlow<List<ArabicVoice>>(emptyList())
    val voices: StateFlow<List<ArabicVoice>> = _voices

    private val _previewing = MutableStateFlow(false)
    val previewing: StateFlow<Boolean> = _previewing

    val maxInputLength: Int get() = TextToSpeech.getMaxSpeechInputLength()

    private val listener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {}

        override fun onDone(utteranceId: String?) = finish(utteranceId, true)

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) = finish(utteranceId, false)

        override fun onError(utteranceId: String?, errorCode: Int) = finish(utteranceId, false)

        override fun onStop(utteranceId: String?, interrupted: Boolean) = finish(utteranceId, false)
    }

    init {
        tts = TextToSpeech(appContext) { code -> onInit(code) }
    }

    private fun finish(utteranceId: String?, ok: Boolean) {
        if (utteranceId == null) return
        if (utteranceId == PREVIEW_ID) {
            _previewing.value = false
            return
        }
        val cont = pending.remove(utteranceId) ?: return
        if (cont.isActive) runCatching { cont.resume(ok) }
    }

    private fun onInit(code: Int) {
        val engine = tts ?: return
        if (code != TextToSpeech.SUCCESS) {
            _status.value = Status.FAILED
            return
        }
        engine.setOnUtteranceProgressListener(listener)
        val langResult = engine.setLanguage(Locale("ar"))

        val arabic = try {
            engine.voices.orEmpty().filter { it.locale.language == "ar" }
        } catch (e: Exception) {
            emptyList()
        }
        voicesByName = arabic.associateBy { it.name }
        _voices.value = arabic
            .sortedWith(compareByDescending<Voice> { it.quality }.thenBy { it.name })
            .mapIndexed { index, v ->
                val network = if (v.isNetworkConnectionRequired) "يحتاج إنترنت" else "محلي"
                ArabicVoice(
                    id = v.name,
                    label = "صوت ${index + 1}",
                    details = "جودة ${qualityLabel(v.quality)} · $network · ${v.name}",
                )
            }

        val usable = arabic.isNotEmpty() ||
            langResult == TextToSpeech.LANG_AVAILABLE ||
            langResult == TextToSpeech.LANG_COUNTRY_AVAILABLE ||
            langResult == TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE
        _status.value = if (usable) Status.READY else Status.NO_ARABIC
    }

    private fun qualityLabel(q: Int): String = when {
        q >= Voice.QUALITY_VERY_HIGH -> "ممتازة"
        q >= Voice.QUALITY_HIGH -> "عالية"
        q >= Voice.QUALITY_NORMAL -> "متوسطة"
        else -> "منخفضة"
    }

    fun selectVoice(name: String?): Boolean {
        val voice = voicesByName[name] ?: return false
        return tts?.setVoice(voice) == TextToSpeech.SUCCESS
    }

    fun configure(rate: Float, pitch: Float) {
        tts?.setSpeechRate(rate)
        tts?.setPitch(pitch)
    }

    /** Renders [text] into [file] (WAV). Returns false on failure, timeout or cancellation. */
    suspend fun synthesizeToFile(text: String, file: File): Boolean {
        val engine = tts ?: return false
        return withTimeoutOrNull(SYNTH_TIMEOUT_MS) {
            suspendCancellableCoroutine<Boolean> { cont ->
                val id = "seg-" + UUID.randomUUID()
                pending[id] = cont
                cont.invokeOnCancellation {
                    pending.remove(id)
                    engine.stop()
                }
                val rc = engine.synthesizeToFile(text, Bundle(), file, id)
                if (rc != TextToSpeech.SUCCESS) {
                    pending.remove(id)?.let { c -> if (c.isActive) runCatching { c.resume(false) } }
                }
            }
        } ?: false
    }

    fun preview(text: String) {
        val engine = tts ?: return
        engine.stop()
        _previewing.value = true
        val rc = engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, PREVIEW_ID)
        if (rc != TextToSpeech.SUCCESS) _previewing.value = false
    }

    fun stopPreview() {
        if (_previewing.value) tts?.stop()
        _previewing.value = false
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }

    private companion object {
        const val PREVIEW_ID = "preview"
        const val SYNTH_TIMEOUT_MS = 120_000L
    }
}

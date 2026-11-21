package org.nationalsounds.app.ui

import android.app.Application
import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.nationalsounds.app.AppPrefs
import org.nationalsounds.app.text.ArabicText
import org.nationalsounds.app.text.RemoteDiacritizer
import org.nationalsounds.app.text.TextProcessor
import org.nationalsounds.app.tts.TtsEngine
import org.nationalsounds.app.tts.WavMerger
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Delivery styles inspired by nature-documentary narration: slow, low, with generous pauses. */
enum class NarrationPreset(
    val label: String,
    val rate: Float,
    val pitch: Float,
    val pause: Float,
) {
    CLASSIC("وثائقي كلاسيكي", 0.88f, 0.85f, 1.0f),
    EPIC("ملحمي عميق", 0.82f, 0.75f, 1.3f),
    CALM("هادئ وانسيابي", 0.93f, 0.92f, 0.85f),
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = AppPrefs(app)
    val engine = TtsEngine(app)

    // ---- Script ---------------------------------------------------------------------------

    var text by mutableStateOf(TextFieldValue(prefs.draft, TextRange(prefs.draft.length)))
        private set

    /** Previous script, kept so tashkeel / clear can be undone. */
    var undoText by mutableStateOf<TextFieldValue?>(null)
        private set

    var isDiacritizing by mutableStateOf(false)
        private set

    // ---- Voice and delivery -------------------------------------------------------------

    var selectedVoiceId by mutableStateOf<String?>(null)
        private set

    var preset by mutableStateOf(NarrationPreset.entries.firstOrNull { it.name == prefs.preset })
        private set

    var rate by mutableFloatStateOf(prefs.rate)
        private set

    var pitch by mutableFloatStateOf(prefs.pitch)
        private set

    var pauseScale by mutableFloatStateOf(prefs.pauseScale)
        private set

    // ---- Generation / result ------------------------------------------------------------

    var isGenerating by mutableStateOf(false)
        private set

    var progress by mutableFloatStateOf(0f)
        private set

    var progressLabel by mutableStateOf("")
        private set

    var result by mutableStateOf<File?>(null)
        private set

    var resultMillis by mutableStateOf(0L)
        private set

    var isPlaying by mutableStateOf(false)
        private set

    // ---- Settings / theme ---------------------------------------------------------------

    var serverUrl by mutableStateOf(prefs.serverUrl)
        private set

    var settingsOpen by mutableStateOf(false)

    var darkOverride by mutableStateOf<Boolean?>(
        when (prefs.darkMode) {
            0 -> false
            1 -> true
            else -> null
        }
    )
        private set

    /** One-shot messages for the snackbar. */
    val messages = Channel<String>(Channel.BUFFERED)

    private var generateJob: Job? = null
    private var saveJob: Job? = null
    private var player: MediaPlayer? = null

    init {
        viewModelScope.launch {
            engine.voices.collect { list ->
                if (list.isNotEmpty() && selectedVoiceId == null) {
                    val saved = prefs.voiceId?.takeIf { id -> list.any { it.id == id } }
                    chooseVoice(saved ?: list.first().id)
                }
            }
        }
    }

    private fun say(message: String) {
        messages.trySend(message)
    }

    // ---- Text editing -------------------------------------------------------------------

    fun onTextChange(value: TextFieldValue) {
        val changed = value.text != text.text
        text = value
        if (changed) persistDraft()
    }

    private fun persistDraft() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(500)
            prefs.draft = text.text
        }
    }

    /** Inserts a diacritic mark at the cursor (or after the selection). */
    fun insertMark(mark: String) {
        val v = text
        val pos = v.selection.max.coerceIn(0, v.text.length)
        val newText = v.text.substring(0, pos) + mark + v.text.substring(pos)
        text = TextFieldValue(newText, TextRange(pos + mark.length))
        persistDraft()
    }

    fun clearText() {
        if (text.text.isEmpty()) return
        undoText = text
        text = TextFieldValue("")
        persistDraft()
    }

    fun undo() {
        val previous = undoText ?: return
        text = previous
        undoText = null
        persistDraft()
    }

    fun removeDiacritics() {
        val current = text
        if (!ArabicText.hasDiacritics(current.text)) {
            say("النص غير مشكَّل أصلًا")
            return
        }
        undoText = current
        val clean = ArabicText.stripDiacritics(current.text)
        text = TextFieldValue(clean, TextRange(clean.length))
        persistDraft()
        say("أُزيل التشكيل من النص")
    }

    /** Auto-tashkeel through the self-hosted server; works on the selection, or the whole text. */
    fun diacritize() {
        if (isDiacritizing) return
        val url = serverUrl.trim()
        if (url.isEmpty()) {
            say("اضبط عنوان خادم التشكيل أولًا")
            settingsOpen = true
            return
        }
        val current = text
        val sel = current.selection
        val hasSelection = !sel.collapsed
        val source = if (hasSelection) current.text.substring(sel.min, sel.max) else current.text
        if (source.isBlank()) {
            say("اكتب نصًّا أولًا")
            return
        }
        viewModelScope.launch {
            isDiacritizing = true
            try {
                val shaped = RemoteDiacritizer(url).diacritize(source)
                undoText = current
                val newText = if (hasSelection) {
                    current.text.replaceRange(sel.min, sel.max, shaped)
                } else {
                    shaped
                }
                text = TextFieldValue(newText, TextRange(newText.length))
                persistDraft()
                say("تم تشكيل النص — راجعه قبل التوليد")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                say("تعذّر التشكيل: ${e.message ?: "خطأ غير معروف"}")
            } finally {
                isDiacritizing = false
            }
        }
    }

    // ---- Voice and delivery -------------------------------------------------------------

    fun chooseVoice(id: String) {
        selectedVoiceId = id
        engine.selectVoice(id)
        prefs.voiceId = id
    }

    fun applyPreset(p: NarrationPreset) {
        preset = p
        rate = p.rate
        pitch = p.pitch
        pauseScale = p.pause
        persistSettings()
    }

    fun setRate(v: Float) { rate = v; preset = null; persistSettings() }
    fun setPitch(v: Float) { pitch = v; preset = null; persistSettings() }
    fun setPauseScale(v: Float) { pauseScale = v; preset = null; persistSettings() }

    private fun persistSettings() {
        prefs.rate = rate
        prefs.pitch = pitch
        prefs.pauseScale = pauseScale
        prefs.preset = preset?.name.orEmpty()
    }

    fun previewVoice() {
        if (engine.status.value != TtsEngine.Status.READY) {
            say("محرك النطق العربي غير جاهز")
            return
        }
        stopPlayback()
        selectedVoiceId?.let { engine.selectVoice(it) }
        engine.configure(rate, pitch)
        val sample = TextProcessor.normalize(text.text).take(220).ifBlank { SAMPLE }
        engine.preview(sample)
    }

    fun stopPreview() = engine.stopPreview()

    // ---- Generation ---------------------------------------------------------------------

    fun generate() {
        if (isGenerating) return
        val source = text.text
        if (source.isBlank()) {
            say("اكتب نص التعليق الصوتي أولًا")
            return
        }
        if (engine.status.value != TtsEngine.Status.READY) {
            say("محرك النطق العربي غير جاهز")
            return
        }
        stopPlayback()
        engine.stopPreview()

        generateJob = viewModelScope.launch {
            isGenerating = true
            progress = 0f
            progressLabel = "جارٍ التحضير…"
            result = null
            resultMillis = 0
            val dir = File(getApplication<Application>().cacheDir, "tts_parts")
            try {
                val maxLen = minOf(engine.maxInputLength - 100, TextProcessor.DEFAULT_MAX_LEN)
                    .coerceAtLeast(120)
                val segments = TextProcessor.segment(source, maxLen, pauseScale)
                if (segments.isEmpty()) {
                    say("لا يوجد نص صالح للنطق")
                    return@launch
                }

                selectedVoiceId?.let { engine.selectVoice(it) }
                engine.configure(rate, pitch)
                withContext(Dispatchers.IO) {
                    dir.deleteRecursively()
                    dir.mkdirs()
                }

                val parts = ArrayList<WavMerger.Part>()
                for ((index, segment) in segments.withIndex()) {
                    progressLabel = "المقطع ${index + 1} من ${segments.size}"
                    val file = File(dir, "part_$index.wav")
                    var ok = engine.synthesizeToFile(segment.text, file) && file.length() > 44
                    if (!ok) ok = engine.synthesizeToFile(segment.text, file) && file.length() > 44
                    if (!ok) {
                        say("فشل توليد المقطع ${index + 1}. جرّب صوتًا آخر أو تأكد من اتصال الإنترنت.")
                        return@launch
                    }
                    parts.add(WavMerger.Part.Audio(file))
                    if (segment.pauseAfterMs > 0) parts.add(WavMerger.Part.Silence(segment.pauseAfterMs))
                    progress = (index + 1f) / segments.size
                }

                progressLabel = "جارٍ دمج المقاطع…"
                val output = File(getApplication<Application>().cacheDir, OUTPUT_NAME)
                val millis = withContext(Dispatchers.IO) { WavMerger.merge(parts, output) }
                result = output
                resultMillis = millis
                say("اكتمل التوليد")
            } catch (e: CancellationException) {
                say("أُلغي التوليد")
                throw e
            } catch (e: Exception) {
                say("تعذّر إكمال التوليد: ${e.message ?: "خطأ غير معروف"}")
            } finally {
                isGenerating = false
                dir.deleteRecursively()
            }
        }
    }

    fun cancelGeneration() {
        generateJob?.cancel()
    }

    // ---- Result: play and save ----------------------------------------------------------

    fun togglePlayback() {
        val file = result ?: return
        if (isPlaying) {
            stopPlayback()
            return
        }
        engine.stopPreview()
        try {
            val mp = MediaPlayer()
            mp.setDataSource(file.absolutePath)
            mp.setOnCompletionListener { stopPlayback() }
            mp.prepare()
            mp.start()
            player = mp
            isPlaying = true
        } catch (e: Exception) {
            stopPlayback()
            say("تعذّر تشغيل الملف")
        }
    }

    private fun stopPlayback() {
        player?.let {
            runCatching { it.stop() }
            it.release()
        }
        player = null
        isPlaying = false
    }

    fun suggestedFileName(): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
        return "national_sounds_$stamp.wav"
    }

    fun saveTo(uri: Uri) {
        val file = result ?: return
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val out = getApplication<Application>().contentResolver.openOutputStream(uri)
                        ?: error("no output stream")
                    out.use { stream -> file.inputStream().use { it.copyTo(stream) } }
                }.isSuccess
            }
            say(if (ok) "تم حفظ الملف بنجاح" else "تعذّر حفظ الملف")
        }
    }

    // ---- Settings -----------------------------------------------------------------------

    fun saveServerUrl(url: String) {
        serverUrl = url.trim()
        prefs.serverUrl = serverUrl
        settingsOpen = false
        say(if (serverUrl.isEmpty()) "أُزيل عنوان الخادم" else "تم حفظ عنوان الخادم")
    }

    fun testServer(url: String) {
        val target = url.trim()
        if (target.isEmpty()) {
            say("اكتب عنوان الخادم أولًا")
            return
        }
        viewModelScope.launch {
            val error = RemoteDiacritizer(target).ping()
            say(if (error == null) "الخادم يعمل بنجاح ✓" else "تعذّر الاتصال بالخادم: $error")
        }
    }

    fun toggleDark(currentlyDark: Boolean) {
        val next = !currentlyDark
        darkOverride = next
        prefs.darkMode = if (next) 1 else 0
    }

    override fun onCleared() {
        prefs.draft = text.text
        stopPlayback()
        engine.shutdown()
        super.onCleared()
    }

    private companion object {
        const val OUTPUT_NAME = "national_sounds_voiceover.wav"
        const val SAMPLE = "في أعماق الغابة الاستوائية، تبدأ رحلةٌ لا تشبه أي رحلة أخرى."
    }
}

package org.nationalsounds.app.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.nationalsounds.app.R
import org.nationalsounds.app.text.ArabicText
import org.nationalsounds.app.text.TextProcessor
import org.nationalsounds.app.tts.ArabicVoice
import org.nationalsounds.app.tts.TtsEngine
import java.util.Locale

/** Width from which the editor and the controls are shown side by side. */
private val TwoPaneBreakpoint = 700.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(vm: MainViewModel, onSave: () -> Unit) {
    val snackbar = remember { SnackbarHostState() }
    val isDark = vm.darkOverride ?: isSystemInDarkTheme()

    LaunchedEffect(Unit) {
        for (message in vm.messages) snackbar.showSnackbar(message)
    }

    // Keep the screen awake while a long narration is being rendered.
    val view = LocalView.current
    DisposableEffect(vm.isGenerating) {
        view.keepScreenOn = vm.isGenerating
        onDispose { view.keepScreenOn = false }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                Column {
                    CenterAlignedTopAppBar(
                        title = {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "أصوات ناشيونال",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp,
                                )
                                Text(
                                    "Text 2 National Sounds",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        actions = {
                            IconButton(onClick = { vm.toggleDark(isDark) }) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_theme),
                                    contentDescription = "تبديل الوضع الداكن",
                                )
                            }
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            titleContentColor = MaterialTheme.colorScheme.onSurface,
                            actionIconContentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            },
        ) { padding ->
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                if (maxWidth >= TwoPaneBreakpoint) {
                    // Tablets, foldables and phones in landscape: editor | controls.
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1.35f)
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            EditorCard(vm, tall = true)
                        }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            VoiceCard(vm)
                            GenerateCard(vm, onSave)
                        }
                    }
                } else {
                    // Phones in portrait: one column.
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                        Column(
                            modifier = Modifier
                                .widthIn(max = 720.dp)
                                .fillMaxWidth()
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            EditorCard(vm, tall = false)
                            VoiceCard(vm)
                            GenerateCard(vm, onSave)
                        }
                    }
                }
            }
        }

        if (vm.settingsOpen) ServerDialog(vm)
    }
}

// ---------------------------------------------------------------------------------------------
// Editor
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditorCard(vm: MainViewModel, tall: Boolean) {
    val stats by remember(vm) {
        derivedStateOf { TextProcessor.analyze(vm.text.text, vm.rate, vm.pauseScale) }
    }

    AppCard(Modifier.fillMaxWidth()) {
        SectionTitle("نص التعليق الصوتي", "اللغة العربية الفصحى فقط — الصق نص الفيلم الوثائقي هنا")

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // The tashkeel icon button.
            FilledTonalButton(
                onClick = vm::diacritize,
                enabled = !vm.isDiacritizing,
                contentPadding = ButtonDefaults.ContentPadding,
            ) {
                if (vm.isDiacritizing) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(
                        painter = painterResource(R.drawable.ic_tashkeel),
                        contentDescription = "تشكيل النص",
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text("تشكيل النص")
            }
            OutlinedButton(onClick = vm::removeDiacritics) { Text("إزالة التشكيل") }
            if (vm.undoText != null) {
                TextButton(onClick = vm::undo) { Text("تراجع") }
            }
            TextButton(
                onClick = vm::clearText,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text("مسح") }
            IconButton(onClick = { vm.settingsOpen = true }) {
                Icon(
                    painter = painterResource(R.drawable.ic_tune),
                    contentDescription = "إعدادات خادم التشكيل",
                )
            }
        }

        // Manual diacritics keypad: inserts the mark at the cursor.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            for ((mark, name) in ArabicText.keypad) {
                AssistChip(
                    onClick = { vm.insertMark(mark) },
                    label = { Text("ـ$mark  $name", fontSize = 15.sp) },
                )
            }
        }

        OutlinedTextField(
            value = vm.text,
            onValueChange = vm::onTextChange,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 240.dp, max = if (tall) 560.dp else 380.dp),
            textStyle = TextStyle(
                fontSize = 19.sp,
                lineHeight = 34.sp,
                textDirection = TextDirection.Rtl,
                textAlign = TextAlign.Start,
            ),
            placeholder = { Text("اكتب أو الصق نص التعليق الصوتي هنا…") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            shape = MaterialTheme.shapes.small,
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (stats.words > 0) {
                Pill(
                    "${stats.words} كلمة · المدة التقريبية ${formatTime(stats.estimatedSeconds)}",
                    MaterialTheme.colorScheme.surfaceVariant,
                    MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (stats.estimatedSeconds < TextProcessor.MIN_TARGET_SECONDS) {
                    Pill(
                        "أقل من ٣ دقائق — أضف مزيدًا من النص",
                        MaterialTheme.colorScheme.tertiaryContainer,
                        MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                } else {
                    Pill(
                        "مدة مناسبة للفيلم الوثائقي",
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            if (stats.latinLetters > 0) {
                Pill(
                    "يوجد ${stats.latinLetters} حرفًا لاتينيًّا — النص المطلوب فصحى فقط",
                    MaterialTheme.colorScheme.errorContainer,
                    MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Voice and delivery
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VoiceCard(vm: MainViewModel) {
    val context = LocalContext.current
    val status by vm.engine.status.collectAsStateWithLifecycle()
    val voices by vm.engine.voices.collectAsStateWithLifecycle()
    val previewing by vm.engine.previewing.collectAsStateWithLifecycle()

    AppCard(Modifier.fillMaxWidth()) {
        SectionTitle("الصوت وأسلوب الأداء", "اختر الصوت ثم اضبط النبرة لتقترب من أسلوب الأفلام الوثائقية")

        when (status) {
            TtsEngine.Status.INITIALIZING -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text("جارٍ تهيئة محرك النطق…")
                }
            }

            TtsEngine.Status.NO_ARABIC, TtsEngine.Status.FAILED -> {
                Pill(
                    if (status == TtsEngine.Status.FAILED) {
                        "تعذّر تشغيل محرك النطق في هذا الجهاز"
                    } else {
                        "لا توجد أصوات عربية مثبّتة على الجهاز"
                    },
                    MaterialTheme.colorScheme.errorContainer,
                    MaterialTheme.colorScheme.onErrorContainer,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(onClick = { installVoiceData(context) }) { Text("تثبيت أصوات عربية") }
                    OutlinedButton(onClick = { openTtsSettings(context) }) { Text("إعدادات النطق") }
                }
            }

            TtsEngine.Status.READY -> {
                if (voices.isNotEmpty()) {
                    VoicePicker(voices, vm.selectedVoiceId, vm::chooseVoice)
                }
            }
        }

        Text("أنماط جاهزة", style = MaterialTheme.typography.labelLarge)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            for (p in NarrationPreset.entries) {
                FilterChip(
                    selected = vm.preset == p,
                    onClick = { vm.applyPreset(p) },
                    label = { Text(p.label) },
                )
            }
        }

        LabeledSlider("سرعة الإلقاء", "%.2f×".format(Locale.US, vm.rate), vm.rate, 0.5f..1.5f, vm::setRate)
        LabeledSlider("غلظة الصوت (النبرة)", "%.2f".format(Locale.US, vm.pitch), vm.pitch, 0.5f..1.5f, vm::setPitch)
        LabeledSlider("طول الوقفات", "%.2f×".format(Locale.US, vm.pauseScale), vm.pauseScale, 0.5f..2.0f, vm::setPauseScale)

        OutlinedButton(
            onClick = { if (previewing) vm.stopPreview() else vm.previewVoice() },
            enabled = status == TtsEngine.Status.READY,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                painter = painterResource(if (previewing) R.drawable.ic_stop else R.drawable.ic_play),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(if (previewing) "إيقاف العيّنة" else "استمع إلى عيّنة")
        }
    }
}

@Composable
private fun VoicePicker(voices: List<ArabicVoice>, selectedId: String?, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val selected = voices.firstOrNull { it.id == selectedId }

    Box {
        OutlinedButton(
            onClick = { open = true },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.small,
        ) {
            Column(Modifier.weight(1f)) {
                Text(selected?.label ?: "اختر الصوت", fontWeight = FontWeight.SemiBold)
                if (selected != null) {
                    Text(
                        selected.details,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text("▾")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for (voice in voices) {
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(voice.label, fontWeight = FontWeight.SemiBold)
                            Text(
                                voice.details,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    onClick = {
                        onSelect(voice.id)
                        open = false
                    },
                )
            }
        }
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                valueText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

// ---------------------------------------------------------------------------------------------
// Generate / result
// ---------------------------------------------------------------------------------------------

@Composable
private fun GenerateCard(vm: MainViewModel, onSave: () -> Unit) {
    val status by vm.engine.status.collectAsStateWithLifecycle()

    AppCard(Modifier.fillMaxWidth()) {
        SectionTitle("إنتاج التعليق الصوتي", "يُولَّد النص مقطعًا مقطعًا ثم يُدمج في ملف WAV واحد")

        if (vm.isGenerating) {
            LinearProgressIndicator(
                progress = { vm.progress },
                modifier = Modifier.fillMaxWidth(),
                trackColor = MaterialTheme.colorScheme.primaryContainer,
            )
            Text(
                vm.progressLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = vm::cancelGeneration,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("إلغاء التوليد") }
        } else {
            Button(
                onClick = vm::generate,
                enabled = status == TtsEngine.Status.READY && vm.text.text.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_play),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text("توليد التعليق الصوتي", fontWeight = FontWeight.Bold)
            }
        }

        if (vm.result != null) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text(
                "المدة النهائية: ${formatTime((vm.resultMillis / 1000).toInt())}",
                fontWeight = FontWeight.SemiBold,
            )
            if (vm.resultMillis < TextProcessor.MIN_TARGET_SECONDS * 1000L) {
                Pill(
                    "المدة أقل من ٣ دقائق",
                    MaterialTheme.colorScheme.tertiaryContainer,
                    MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = vm::togglePlayback, modifier = Modifier.weight(1f)) {
                    Icon(
                        painter = painterResource(if (vm.isPlaying) R.drawable.ic_stop else R.drawable.ic_play),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(if (vm.isPlaying) "إيقاف" else "استماع")
                }
                Button(onClick = onSave, modifier = Modifier.weight(1f)) {
                    Icon(
                        painter = painterResource(R.drawable.ic_save),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("حفظ WAV")
                }
            }
        }

        Text(
            "الملف بصيغة WAV غير مضغوطة لأعلى جودة عند المونتاج، ويُحفظ في المكان الذي تختاره.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Server settings dialog
// ---------------------------------------------------------------------------------------------

@Composable
private fun ServerDialog(vm: MainViewModel) {
    var url by remember { mutableStateOf(vm.serverUrl) }

    AlertDialog(
        onDismissRequest = { vm.settingsOpen = false },
        title = { Text("خادم التشكيل الآلي") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "التشكيل الآلي يعمل عبر خادم صغير مفتوح المصدر تشغّله بنفسك " +
                        "(مجلد server في المستودع). أدخل عنوانه هنا.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    singleLine = true,
                    label = { Text("عنوان الخادم") },
                    placeholder = { Text("http://192.168.1.10:8765/diacritize") },
                    textStyle = TextStyle(textDirection = TextDirection.Ltr, textAlign = TextAlign.Left),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = { vm.testServer(url) }) { Text("اختبار الاتصال") }
            }
        },
        confirmButton = { TextButton(onClick = { vm.saveServerUrl(url) }) { Text("حفظ") } },
        dismissButton = { TextButton(onClick = { vm.settingsOpen = false }) { Text("إلغاء") } },
    )
}

// ---------------------------------------------------------------------------------------------
// Small pieces
// ---------------------------------------------------------------------------------------------

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Pill(text: String, container: Color, content: Color) {
    Surface(color = container, contentColor = content, shape = MaterialTheme.shapes.small) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

private fun formatTime(totalSeconds: Int): String =
    String.format(Locale.US, "%d:%02d", totalSeconds / 60, totalSeconds % 60)

private fun openTtsSettings(context: Context) {
    val candidates = listOf(Intent("com.android.settings.TTS_SETTINGS"), Intent(Settings.ACTION_SETTINGS))
    for (intent in candidates) {
        try {
            context.startActivity(intent)
            return
        } catch (_: Exception) {
            // try the next one
        }
    }
}

private fun installVoiceData(context: Context) {
    try {
        context.startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA))
    } catch (_: Exception) {
        openTtsSettings(context)
    }
}

package org.nationalsounds.app

import android.content.Context

/** Tiny SharedPreferences wrapper: script draft, voice settings, server URL, theme. */
class AppPrefs(context: Context) {

    private val sp = context.getSharedPreferences("national_sounds", Context.MODE_PRIVATE)

    var draft: String
        get() = sp.getString("draft", "").orEmpty()
        set(value) { sp.edit().putString("draft", value).apply() }

    var voiceId: String?
        get() = sp.getString("voice", null)
        set(value) { sp.edit().putString("voice", value).apply() }

    var rate: Float
        get() = sp.getFloat("rate", 0.88f)
        set(value) { sp.edit().putFloat("rate", value).apply() }

    var pitch: Float
        get() = sp.getFloat("pitch", 0.85f)
        set(value) { sp.edit().putFloat("pitch", value).apply() }

    var pauseScale: Float
        get() = sp.getFloat("pause", 1.0f)
        set(value) { sp.edit().putFloat("pause", value).apply() }

    /** Name of the selected preset, or empty when the sliders were adjusted by hand. */
    var preset: String
        get() = sp.getString("preset", "CLASSIC").orEmpty()
        set(value) { sp.edit().putString("preset", value).apply() }

    var serverUrl: String
        get() = sp.getString("server", "").orEmpty()
        set(value) { sp.edit().putString("server", value).apply() }

    /** -1 = follow the system, 0 = light, 1 = dark. */
    var darkMode: Int
        get() = sp.getInt("dark", -1)
        set(value) { sp.edit().putInt("dark", value).apply() }
}

package org.nationalsounds.app.text

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Adds tashkeel (diacritics) to Arabic text. */
interface Diacritizer {
    suspend fun diacritize(text: String): String
}

/**
 * Talks to the small open-source server in the repository's `server/` folder:
 *
 *   POST {endpoint}   {"text": "..."}   ->   {"text": "..."}
 *   GET  /health                        ->   {"ok": true}
 */
class RemoteDiacritizer(private val endpoint: String) : Diacritizer {

    override suspend fun diacritize(text: String): String = withContext(Dispatchers.IO) {
        val source = ArabicText.stripDiacritics(text)
        val sb = StringBuilder()
        for (chunk in TextProcessor.chunkForRemote(source, CHUNK_SIZE)) {
            val core = chunk.trim()
            if (core.isEmpty()) {
                sb.append(chunk)
                continue
            }
            val lead = chunk.takeWhile { it.isWhitespace() }
            val trail = chunk.takeLastWhile { it.isWhitespace() }
            sb.append(lead).append(post(core)).append(trail)
        }
        sb.toString()
    }

    /** Returns null when the server answers, otherwise a short error description. */
    suspend fun ping(): String? = withContext(Dispatchers.IO) {
        try {
            val url = URL(URL(endpoint), "/health")
            val conn = url.openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 6_000
                conn.readTimeout = 6_000
                val code = conn.responseCode
                if (code in 200..299) null else "رمز الاستجابة $code"
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            e.message ?: "تعذّر الاتصال"
        }
    }

    private fun post(text: String): String {
        val conn = URL(endpoint).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 8_000
            conn.readTimeout = 60_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.outputStream.use {
                it.write(JSONObject().put("text", text).toString().toByteArray(Charsets.UTF_8))
            }
            val code = conn.responseCode
            if (code !in 200..299) throw IOException("رمز الاستجابة $code")
            val body = conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
            return JSONObject(body).getString("text")
        } finally {
            conn.disconnect()
        }
    }

    private companion object {
        const val CHUNK_SIZE = 1_200
    }
}

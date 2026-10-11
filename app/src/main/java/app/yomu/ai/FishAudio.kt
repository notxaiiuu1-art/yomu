package app.yomu.ai

import app.yomu.Yomu
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** Fish Audio text-to-speech: turns a line into an MP3 spoken by a chosen voice model. */
object FishAudio {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .callTimeout(120, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    private val dir by lazy { File(Yomu.app.cacheDir, "fish_tts").apply { mkdirs() } }

    fun voiceFor(speaker: Speaker): String {
        val prefs = Yomu.prefs
        return when (speaker) {
            Speaker.YOUNG_WOMAN -> prefs.fishYoungWoman.get()
            Speaker.WOMAN -> prefs.fishWoman.get()
            Speaker.MAN -> prefs.fishMan.get()
            Speaker.OLD_MAN -> prefs.fishOldMan.get()
            Speaker.NARRATOR -> prefs.fishNarrator.get()
        }.trim()
    }

    /** Returns the cached MP3 for [text] in [voiceId], generating it first if needed. */
    suspend fun audio(text: String, voiceId: String): File = withContext(Dispatchers.IO) {
        val prefs = Yomu.prefs
        val key = prefs.fishKey.get().trim()
        if (key.isEmpty()) throw IOException("Kein Fish-Audio-Key. Trag ihn unter Einstellungen → KI-Vorlesen ein.")
        if (voiceId.isEmpty()) throw IOException("Für diese Stimme ist keine Fish-Audio-ID eingetragen.")
        val model = prefs.fishModel.get().trim().ifEmpty { "s2.1-pro" }
        val speed = prefs.ttsRate.get().coerceIn(0.5f, 2.0f)

        val file = File(dir, md5("$model|$voiceId|$speed|$text") + ".mp3")
        if (file.exists() && file.length() > 0) return@withContext file

        val body = buildJsonObject {
            put("text", text)
            put("reference_id", voiceId)
            put("format", "mp3")
            put("latency", "balanced")
            putJsonObject("prosody") { put("speed", speed) }
        }
        val request = Request.Builder()
            .url("https://api.fish.audio/v1/tts")
            .header("Authorization", "Bearer $key")
            .header("model", model)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val raw = response.body.string()
                val message = runCatching {
                    json.parseToJsonElement(raw).jsonObject["message"]?.jsonPrimitive?.contentOrNull
                }.getOrNull() ?: raw.take(160)
                val hint = when (response.code) {
                    401 -> " (Key ungültig)"
                    402 -> " (Guthaben aufgebraucht)"
                    else -> ""
                }
                throw IOException("Fish-Audio-Fehler ${response.code}$hint: $message")
            }
            val tmp = File(dir, file.name + ".tmp")
            tmp.outputStream().use { out -> response.body.byteStream().copyTo(out) }
            if (tmp.length() == 0L) {
                tmp.delete()
                throw IOException("Fish Audio hat keine Audiodaten geliefert.")
            }
            tmp.renameTo(file)
        }
        file
    }

    private fun md5(text: String) =
        MessageDigest.getInstance("MD5").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
}

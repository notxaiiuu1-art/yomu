package app.yomu.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.util.Base64
import app.yomu.Yomu
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

enum class Speaker { FEMALE, MALE, CHILD, NARRATOR }

@Serializable
data class SpokenLine(
    val speaker: Speaker,
    val name: String = "",
    val text: String,
    /** Vertical centre of the bubble as a fraction of the page height (0 = top, 1 = bottom), or -1 if unknown. */
    val y: Float = -1f,
)

@Serializable
data class PageScript(val language: String = "", val lines: List<SpokenLine> = emptyList())

/**
 * Sends a comic page to Gemini and gets back every speech bubble in reading order, tagged with
 * who speaks (woman / man / child / narrator). Results are cached on disk per page image.
 */
object Gemini {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .callTimeout(150, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val cacheDir by lazy { File(Yomu.app.filesDir, "ai_scripts").apply { mkdirs() } }

    suspend fun readPage(image: File, rightToLeft: Boolean): PageScript = withContext(Dispatchers.IO) {
        val prefs = Yomu.prefs
        val key = prefs.geminiKey.get().trim()
        if (key.isEmpty()) throw IOException("Kein Gemini-API-Key. Trag ihn unter Einstellungen → KI-Vorlesen ein.")
        val model = prefs.geminiModel.get().trim().ifEmpty { "gemini-3.8-flash" }
        val translate = prefs.aiTranslateGerman.get()

        val cacheFile = File(cacheDir, md5("v2|${image.name}|${image.length()}|$model|$translate|$rightToLeft") + ".json")
        if (cacheFile.exists()) {
            runCatching { return@withContext json.decodeFromString<PageScript>(cacheFile.readText()) }
        }

        val parts = imageParts(image)
        val body = buildJsonObject {
            putJsonArray("contents") {
                addJsonObject {
                    put("role", "user")
                    putJsonArray("parts") {
                        parts.forEach { b64 ->
                            addJsonObject {
                                putJsonObject("inline_data") {
                                    put("mime_type", "image/jpeg")
                                    put("data", b64)
                                }
                            }
                        }
                        addJsonObject { put("text", prompt(rightToLeft, translate, parts.size)) }
                    }
                }
            }
            putJsonObject("generationConfig") {
                put("temperature", 0.2)
                put("response_mime_type", "application/json")
            }
            putJsonArray("safetySettings") {
                listOf(
                    "HARM_CATEGORY_SEXUALLY_EXPLICIT",
                    "HARM_CATEGORY_HARASSMENT",
                    "HARM_CATEGORY_HATE_SPEECH",
                    "HARM_CATEGORY_DANGEROUS_CONTENT",
                ).forEach { category ->
                    addJsonObject {
                        put("category", category)
                        put("threshold", "BLOCK_NONE")
                    }
                }
            }
        }

        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
            .header("x-goog-api-key", key)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val text = client.newCall(request).execute().use { response ->
            val raw = response.body.string()
            if (!response.isSuccessful) {
                val message = runCatching {
                    json.parseToJsonElement(raw).jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
                }.getOrNull()
                throw IOException("Gemini-Fehler ${response.code}: ${message ?: raw.take(200)}")
            }
            val root = json.parseToJsonElement(raw).jsonObject
            val candidate = root["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
                ?: throw IOException("Gemini hat die Seite abgelehnt (Inhaltsfilter).")
            candidate["content"]?.jsonObject?.get("parts")?.jsonArray
                ?.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull }
                ?.joinToString("")
                ?: throw IOException("Gemini hat keinen Text geliefert (${candidate["finishReason"]}).")
        }

        val script = parse(text)
        runCatching { cacheFile.writeText(json.encodeToString(PageScript.serializer(), script)) }
        script
    }

    private fun prompt(rightToLeft: Boolean, translate: Boolean, imageCount: Int) = buildString {
        append("You turn a comic page into an audiobook script. ")
        if (imageCount > 1) append("The $imageCount images are consecutive slices of ONE tall page, top to bottom. ")
        append("Extract every speech bubble, thought bubble and narration box in natural reading order ")
        append(if (rightToLeft) "(Japanese manga: panels right-to-left, top-to-bottom). " else "(panels left-to-right, top-to-bottom). ")
        append("Ignore sound effects, watermarks, website names, page numbers and credits. ")
        append("For each line decide who speaks by looking at the character the bubble points to: ")
        append("\"female\" for a woman or girl, \"male\" for a man or boy, \"child\" for a small child, ")
        append("\"narrator\" for captions and narration boxes. ")
        if (translate) {
            append("Translate every line into natural, spoken German. Keep names unchanged. ")
        } else {
            append("Keep the original language and wording. ")
        }
        append("Reply ONLY with JSON: {\"language\":\"<ISO 639-1 code of the text you output>\",")
        append("\"lines\":[{\"speaker\":\"female|male|child|narrator\",\"name\":\"<character name or empty>\",")
        append("\"y\":<vertical centre of the bubble as a fraction of the WHOLE page height, 0.0 = top, 1.0 = bottom>,\"text\":\"...\"}]}. ")
        append("If the page has no readable text, reply {\"language\":\"\",\"lines\":[]}.")
    }

    private fun parse(text: String): PageScript {
        val cleaned = text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val root = json.parseToJsonElement(cleaned)
        val obj = root as? JsonObject
        val array = (obj?.get("lines") as? JsonArray) ?: (root as? JsonArray) ?: JsonArray(emptyList())
        val lines = array.mapNotNull { element ->
            val o = element as? JsonObject ?: return@mapNotNull null
            val line = o["text"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            if (line.isEmpty()) return@mapNotNull null
            val speaker = when (o["speaker"]?.jsonPrimitive?.contentOrNull?.lowercase()?.trim()) {
                "female", "woman", "girl", "f" -> Speaker.FEMALE
                "male", "man", "boy", "m" -> Speaker.MALE
                "child", "kid" -> Speaker.CHILD
                else -> Speaker.NARRATOR
            }
            val y = o["y"]?.jsonPrimitive?.contentOrNull?.toFloatOrNull()?.takeIf { it in 0f..1f } ?: -1f
            SpokenLine(speaker, o["name"]?.jsonPrimitive?.contentOrNull.orEmpty(), line, y)
        }
        return PageScript(obj?.get("language")?.jsonPrimitive?.contentOrNull.orEmpty(), lines)
    }

    /**
     * JPEG slices small enough to upload quickly but sharp enough to read: width ≤ 1280 px and,
     * for long webtoon strips, slices with an aspect ratio of at most 1 : 2.
     */
    private fun imageParts(file: File): List<String> {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val w = bounds.outWidth
        val h = bounds.outHeight
        if (w <= 0 || h <= 0) throw IOException("Bild kann nicht gelesen werden")

        val sliceHeight = (w * 2).coerceAtLeast(1)
        val slices = ((h + sliceHeight - 1) / sliceHeight).coerceIn(1, 8)
        val step = (h + slices - 1) / slices
        val sample = if (w > 2560) 2 else 1

        val decoder = BitmapRegionDecoder.newInstance(file.absolutePath)
        try {
            return (0 until slices).map { i ->
                val top = i * step
                val rect = Rect(0, top, w, minOf(h, top + step))
                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                var bmp = decoder.decodeRegion(rect, opts) ?: throw IOException("Bild kann nicht gelesen werden")
                if (bmp.width > 1280) {
                    val scaled = Bitmap.createScaledBitmap(bmp, 1280, (bmp.height * 1280f / bmp.width).toInt(), true)
                    if (scaled !== bmp) bmp.recycle()
                    bmp = scaled
                }
                val out = ByteArrayOutputStream()
                bmp.compress(Bitmap.CompressFormat.JPEG, 88, out)
                bmp.recycle()
                Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
            }
        } finally {
            decoder.recycle()
        }
    }

    private fun md5(text: String) =
        MessageDigest.getInstance("MD5").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
}

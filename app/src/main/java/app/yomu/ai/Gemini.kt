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

enum class Speaker(val label: String) {
    YOUNG_WOMAN("Junge Frau"),
    WOMAN("Frau"),
    YOUNG_MAN("Junger Mann"),
    MAN("Mann"),
    OLD_MAN("Älterer Mann"),
    NARRATOR("Erzähler"),
}

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

    /** Free-tier quota is per model; this one usually has a much higher daily limit. */
    private const val FALLBACK_MODEL = "gemini-3.5-flash-lite"

    /** Maximum pages and image slices sent in one request (fewer requests = more pages per day). */
    private const val MAX_PAGES_PER_REQUEST = 6
    private const val MAX_IMAGES_PER_REQUEST = 14

    class QuotaException(message: String) : IOException(message)

    private fun cacheFileFor(image: File, translate: Boolean, rightToLeft: Boolean) =
        File(cacheDir, md5("v5|${image.name}|${image.length()}|$translate|$rightToLeft") + ".json")

    fun cached(image: File, rightToLeft: Boolean): PageScript? {
        val file = cacheFileFor(image, Yomu.prefs.aiTranslateGerman.get(), rightToLeft)
        return if (file.exists()) runCatching { json.decodeFromString<PageScript>(file.readText()) }.getOrNull() else null
    }

    /**
     * Reads several pages with as few requests as possible. Returns one script per input file,
     * in the same order. Already analysed pages come from the disk cache.
     */
    suspend fun readPages(images: List<File>, rightToLeft: Boolean): List<PageScript> = withContext(Dispatchers.IO) {
        val prefs = Yomu.prefs
        val key = prefs.geminiKey.get().trim()
        if (key.isEmpty()) throw IOException("Kein Gemini-API-Key. Trag ihn unter Einstellungen → KI-Vorlesen ein.")
        val model = prefs.geminiModel.get().trim().ifEmpty { "gemini-3.8-flash" }
        val translate = prefs.aiTranslateGerman.get()

        val results = arrayOfNulls<PageScript>(images.size)
        images.forEachIndexed { i, file -> results[i] = cached(file, rightToLeft) }

        // Group the missing pages into requests that stay within the image budget.
        val missing = images.indices.filter { results[it] == null }
        val prepared = missing.associateWith { imageParts(images[it]) }
        val groups = mutableListOf<MutableList<Int>>()
        for (i in missing) {
            val group = groups.lastOrNull()
            val used = group?.sumOf { prepared.getValue(it).size } ?: 0
            if (group == null || group.size >= MAX_PAGES_PER_REQUEST || used + prepared.getValue(i).size > MAX_IMAGES_PER_REQUEST) {
                groups += mutableListOf(i)
            } else {
                group += i
            }
        }

        for (group in groups) {
            val pages = group.map { prepared.getValue(it) }
            val scripts = try {
                request(key, model, pages, rightToLeft, translate)
            } catch (quota: QuotaException) {
                if (model == FALLBACK_MODEL) throw quota
                // Daily limit of the main model used up: try the lighter model before giving up.
                try {
                    request(key, FALLBACK_MODEL, pages, rightToLeft, translate)
                } catch (_: IOException) {
                    throw quota
                }
            }
            group.forEachIndexed { n, i ->
                val script = scripts.getOrNull(n) ?: PageScript()
                results[i] = script
                runCatching {
                    cacheFileFor(images[i], translate, rightToLeft)
                        .writeText(json.encodeToString(PageScript.serializer(), script))
                }
            }
        }
        results.map { it ?: PageScript() }
    }

    suspend fun readPage(image: File, rightToLeft: Boolean): PageScript = readPages(listOf(image), rightToLeft).first()

    private fun request(
        key: String,
        model: String,
        pages: List<List<String>>,
        rightToLeft: Boolean,
        translate: Boolean,
    ): List<PageScript> {
        val body = buildJsonObject {
            putJsonArray("contents") {
                addJsonObject {
                    put("role", "user")
                    putJsonArray("parts") {
                        pages.forEachIndexed { index, slices ->
                            addJsonObject { put("text", "PAGE ${index + 1}" + if (slices.size > 1) " (${slices.size} slices, top to bottom)" else "") }
                            slices.forEach { b64 ->
                                addJsonObject {
                                    putJsonObject("inline_data") {
                                        put("mime_type", "image/jpeg")
                                        put("data", b64)
                                    }
                                }
                            }
                        }
                        addJsonObject { put("text", prompt(rightToLeft, translate, pages.size)) }
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
                }.getOrNull() ?: raw.take(200)
                if (response.code == 429) throw QuotaException(quotaMessage(message))
                throw IOException("Gemini-Fehler ${response.code}: $message")
            }
            val root = json.parseToJsonElement(raw).jsonObject
            val candidate = root["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
                ?: throw IOException("Gemini hat die Seite abgelehnt (Inhaltsfilter).")
            candidate["content"]?.jsonObject?.get("parts")?.jsonArray
                ?.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull }
                ?.joinToString("")
                ?: throw IOException("Gemini hat keinen Text geliefert (${candidate["finishReason"]}).")
        }
        return parsePages(text, pages.size)
    }

    private fun quotaMessage(raw: String): String {
        val wait = Regex("""retry in (?:(\d+)h)?(?:(\d+)m)?(\d+)(?:\.\d+)?s""").find(raw)?.let { m ->
            val h = m.groupValues[1].toIntOrNull() ?: 0
            val min = m.groupValues[2].toIntOrNull() ?: 0
            val minutes = h * 60 + min + 1
            if (minutes >= 60) "in ca. ${minutes / 60} Std. ${minutes % 60} Min." else "in ca. $minutes Min."
        }
        val limit = Regex("""limit: (\d+)""").find(raw)?.groupValues?.get(1)
        return buildString {
            append("Gratis-Limit von Gemini erreicht")
            if (limit != null) append(" ($limit Anfragen)")
            append(". ")
            append(if (wait != null) "Wieder möglich $wait " else "Später nochmal versuchen. ")
            append("Tipp: In den Einstellungen ein anderes Modell eintragen oder bei Google AI Studio Abrechnung aktivieren.")
        }
    }

    private fun prompt(rightToLeft: Boolean, translate: Boolean, pageCount: Int) = buildString {
        append("You turn comic pages into an audiobook script. There are $pageCount page(s), each introduced by a \"PAGE n\" label; ")
        append("a page given as several slices is one tall page, top to bottom. ")
        append("For every page, extract every speech bubble, thought bubble and narration box in natural reading order ")
        append(if (rightToLeft) "(Japanese manga: panels right-to-left, top-to-bottom). " else "(panels left-to-right, top-to-bottom). ")
        append("Ignore sound effects, watermarks, website names, page numbers and credits. ")
        append("For each line decide who speaks by looking at the character the bubble points to: ")
        append("\"young_woman\" for a young adult woman, \"woman\" for a mature woman, ")
        append("\"young_man\" for a young adult man (late teens to about 30), \"man\" for a grown, middle-aged man, ")
        append("\"old_man\" for an elderly man, \"narrator\" for captions and narration boxes. ")
        if (translate) {
            append("Translate every line into natural, spoken German. Keep names unchanged. ")
        } else {
            append("Keep the original language and wording. ")
        }
        append("Reply ONLY with JSON: {\"pages\":[{\"page\":<n>,\"language\":\"<ISO 639-1 code of the text you output>\",")
        append("\"lines\":[{\"speaker\":\"young_woman|woman|young_man|man|old_man|narrator\",\"name\":\"<character name or empty>\",")
        append("\"y\":<vertical centre of the bubble as a fraction of that page's full height, 0.0 = top, 1.0 = bottom>,\"text\":\"...\"}]}]}. ")
        append("Include every page, with \"lines\":[] when a page has no readable text.")
    }

    private fun parsePages(text: String, count: Int): List<PageScript> {
        val cleaned = text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val root = json.parseToJsonElement(cleaned)
        val pagesArray = (root as? JsonObject)?.get("pages") as? JsonArray
        if (pagesArray == null) {
            // Model answered in the single-page shape.
            return listOf(parse(cleaned)) + List((count - 1).coerceAtLeast(0)) { PageScript() }
        }
        val byNumber = pagesArray.mapNotNull { it as? JsonObject }.associateBy {
            it["page"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: -1
        }
        return (1..count).map { n ->
            val page = byNumber[n] ?: pagesArray.getOrNull(n - 1) as? JsonObject
            if (page == null) PageScript() else parse(page.toString())
        }
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
                "young_woman", "young woman", "female", "f" -> Speaker.YOUNG_WOMAN
                "woman", "mature_woman", "older_woman" -> Speaker.WOMAN
                "young_man", "young man", "young_adult_man", "boy" -> Speaker.YOUNG_MAN
                "man", "male", "m" -> Speaker.MAN
                "old_man", "old man", "elderly_man" -> Speaker.OLD_MAN
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

package app.yomu.ai

import app.yomu.Yomu
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** A Fish Audio voice the user saved under their own name. */
@Serializable
data class SavedVoice(val name: String, val id: String)

/** A voice found in the public Fish Audio library. */
data class FishSearchResult(val id: String, val title: String, val description: String, val languages: List<String>)

/** The user's own collection of Fish Audio voices, plus lookup and search in the Fish Audio library. */
object FishVoices {

    private val json = Json { ignoreUnknownKeys = true }
    private val file by lazy { File(Yomu.app.filesDir, "fish_voices.json") }
    private val idRegex = Regex("[0-9a-fA-F]{32}")

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val state by lazy {
        val saved = runCatching { json.decodeFromString<List<SavedVoice>>(file.readText()) }.getOrElse { emptyList() }
        val previous = Yomu.prefs.takePreviousYoungWoman()
        val initial = if (previous != null && saved.none { it.id == previous }) {
            saved + SavedVoice("Junge Frau (vorher)", previous)
        } else {
            saved
        }
        MutableStateFlow(initial).also { if (initial != saved) write(initial) }
    }

    val all: StateFlow<List<SavedVoice>> get() = state.asStateFlow()

    /** Pulls the 32-character voice ID out of a raw ID or any fish.audio link. */
    fun parseId(input: String): String? = idRegex.find(input.trim())?.value?.lowercase()

    /** Saves a voice (or renames it if the ID is already saved). Returns false if no ID was found. */
    fun add(name: String, idOrLink: String): Boolean {
        val id = parseId(idOrLink) ?: return false
        val clean = name.trim().ifEmpty { "Stimme ${id.take(6)}" }
        state.update { list ->
            if (list.any { it.id == id }) list.map { if (it.id == id) it.copy(name = clean) else it }
            else list + SavedVoice(clean, id)
        }
        save()
        return true
    }

    fun remove(id: String) {
        state.update { list -> list.filterNot { it.id == id } }
        save()
    }

    fun nameFor(id: String): String? = state.value.firstOrNull { it.id == id }?.name

    /** The voice's title on Fish Audio, used to fill in the name automatically. */
    suspend fun lookupTitle(id: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val obj = get("https://api.fish.audio/model/$id")
            obj["title"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
        }.getOrNull()
    }

    /** Searches the public Fish Audio voice library by title. */
    suspend fun search(query: String): List<FishSearchResult> = withContext(Dispatchers.IO) {
        val url = "https://api.fish.audio/model".toHttpUrl().newBuilder()
            .addQueryParameter("title", query.trim())
            .addQueryParameter("page_size", "20")
            .addQueryParameter("sort_by", "score")
            .build()
            .toString()
        val items = get(url)["items"] as? JsonArray ?: return@withContext emptyList()
        items.mapNotNull { element ->
            val o = element as? JsonObject ?: return@mapNotNull null
            val id = o["_id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            FishSearchResult(
                id = id,
                title = o["title"]?.jsonPrimitive?.contentOrNull.orEmpty().ifBlank { id.take(8) },
                description = o["description"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                languages = (o["languages"] as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty(),
            )
        }
    }

    private fun get(url: String): JsonObject {
        val key = Yomu.prefs.fishKey.get().trim()
        if (key.isEmpty()) throw IOException("Kein Fish-Audio-Key. Trag ihn oben ein.")
        val request = Request.Builder().url(url).header("Authorization", "Bearer $key").get().build()
        client.newCall(request).execute().use { response ->
            val raw = response.body.string()
            if (!response.isSuccessful) {
                val hint = if (response.code == 401) " (Key ungültig)" else ""
                throw IOException("Fish-Audio-Fehler ${response.code}$hint")
            }
            return json.parseToJsonElement(raw).jsonObject
        }
    }

    @Synchronized
    private fun save() = write(state.value)

    private fun write(list: List<SavedVoice>) {
        runCatching { file.writeText(json.encodeToString(list)) }
    }
}

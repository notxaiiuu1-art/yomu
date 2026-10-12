package app.yomu.ai

import app.yomu.Yomu
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class MangaCast(
    /** Character name → Fish Audio voice ID. */
    val characters: Map<String, String> = emptyMap(),
    val setupDone: Boolean = false,
)

/** Per-comic voice casting: which voice each main character speaks with. */
object VoiceCasting {

    private val json = Json { ignoreUnknownKeys = true }
    private val file by lazy { File(Yomu.app.filesDir, "voice_cast.json") }

    private val state by lazy {
        MutableStateFlow(
            runCatching { json.decodeFromString<Map<String, MangaCast>>(file.readText()) }.getOrElse { emptyMap() },
        )
    }

    val all: StateFlow<Map<String, MangaCast>> get() = state.asStateFlow()

    fun cast(mangaKey: String): MangaCast = state.value[mangaKey] ?: MangaCast()

    fun setVoice(mangaKey: String, name: String, voiceId: String?) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        state.update { map ->
            val old = map[mangaKey] ?: MangaCast()
            val chars = if (voiceId.isNullOrBlank()) old.characters - clean else old.characters + (clean to voiceId)
            map + (mangaKey to old.copy(characters = chars))
        }
        save()
    }

    fun markDone(mangaKey: String) {
        state.update { map -> map + (mangaKey to (map[mangaKey] ?: MangaCast()).copy(setupDone = true)) }
        save()
    }

    /** Voice for a character, matching names loosely ("Tsunade" also matches "Lady Tsunade"). */
    fun voiceFor(mangaKey: String, name: String): String? {
        val n = name.trim()
        if (n.length < 2) return null
        val chars = cast(mangaKey).characters
        chars.entries.firstOrNull { it.key.equals(n, ignoreCase = true) }?.let { return it.value }
        return chars.entries.firstOrNull { (key, _) ->
            key.length >= 3 && (n.contains(key, ignoreCase = true) || key.contains(n, ignoreCase = true))
        }?.value
    }

    /** The voices the user set up for the speaker types, offered when casting characters. */
    fun presets(): List<Pair<String, String>> {
        val p = Yomu.prefs
        return listOf(
            "Junge Frau" to p.fishYoungWoman.get(),
            "Frau (älter)" to p.fishWoman.get(),
            "Mann" to p.fishMan.get(),
            "Älterer Mann" to p.fishOldMan.get(),
            "Erzähler" to p.fishNarrator.get(),
        ).filter { it.second.isNotBlank() }
    }

    @Synchronized
    private fun save() {
        runCatching { file.writeText(json.encodeToString(state.value)) }
    }
}

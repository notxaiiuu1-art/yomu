package app.yomu.core

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class ReaderMode(val label: String) {
    AUTO("Automatisch"),
    WEBTOON("Webtoon (vertikal, endlos)"),
    PAGED_RTL("Manga (rechts → links)"),
    PAGED_LTR("Comic (links → rechts)"),
    PAGED_VERTICAL("Seiten vertikal"),
}

enum class NsfwFilter(val label: String) {
    HIDE("Ohne 18+"),
    SHOW("Alle"),
    ONLY("Nur 18+"),
    ;

    fun allows(nsfw: Boolean) = when (this) {
        HIDE -> !nsfw
        SHOW -> true
        ONLY -> nsfw
    }
}

enum class LibrarySort(val label: String) {
    LAST_READ("Zuletzt gelesen"),
    UNREAD("Ungelesen"),
    TITLE("Titel"),
    ADDED("Hinzugefügt"),
    UPDATED("Neueste Kapitel"),
}

/** A single persisted setting exposed as a [StateFlow] so Compose recomposes on change. */
class Pref<T>(
    private val sp: SharedPreferences,
    private val key: String,
    private val default: T,
    private val read: SharedPreferences.(String, T) -> T,
    private val write: SharedPreferences.Editor.(String, T) -> Unit,
) {
    private val state = MutableStateFlow(sp.read(key, default))
    val flow: StateFlow<T> = state.asStateFlow()

    fun get(): T = state.value

    fun set(value: T) {
        state.value = value
        sp.edit { write(key, value) }
    }
}

class Prefs(context: Context) {
    private val sp: SharedPreferences = context.getSharedPreferences("yomu", Context.MODE_PRIVATE)

    private fun bool(key: String, default: Boolean) =
        Pref(sp, key, default, { k, d -> getBoolean(k, d) }, { k, v -> putBoolean(k, v) })

    private fun int(key: String, default: Int) =
        Pref(sp, key, default, { k, d -> getInt(k, d) }, { k, v -> putInt(k, v) })

    private fun float(key: String, default: Float) =
        Pref(sp, key, default, { k, d -> getFloat(k, d) }, { k, v -> putFloat(k, v) })

    private fun string(key: String, default: String) =
        Pref(sp, key, default, { k, d -> getString(k, d) ?: d }, { k, v -> putString(k, v) })

    private fun strings(key: String, default: Set<String>) =
        Pref(sp, key, default, { k, d -> getStringSet(k, d)?.toSet() ?: d }, { k, v -> putStringSet(k, v) })

    private inline fun <reified E : Enum<E>> enum(key: String, default: E) = Pref(
        sp,
        key,
        default,
        { k, d -> getString(k, null)?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: d },
        { k, v -> putString(k, v.name) },
    )

    // Look
    val themeMode = enum("theme_mode", ThemeMode.SYSTEM)
    val amoledBlack = bool("amoled_black", true)
    val dynamicColor = bool("dynamic_color", true)
    val gridColumns = int("grid_columns", 3)
    val force120Hz = bool("force_120hz", true)

    // Library
    val librarySort = enum("library_sort", LibrarySort.LAST_READ)
    val showUnreadBadge = bool("unread_badge", true)
    val updateIntervalHours = int("update_interval_hours", 12)
    val updateOnlyWifi = bool("update_only_wifi", false)
    val autoDownloadNew = bool("auto_download_new", false)

    // Reader
    val readerMode = enum("reader_mode", ReaderMode.AUTO)
    val keepScreenOn = bool("keep_screen_on", true)
    val volumeKeys = bool("volume_keys", true)
    val invertTapZones = bool("invert_tap_zones", false)
    val showPageNumber = bool("show_page_number", true)
    val webtoonGapDp = int("webtoon_gap", 0)
    val webtoonSidePadding = int("webtoon_side_padding", 0)
    val preloadPages = int("preload_pages", 6)
    val downloadAhead = int("download_ahead", 2)
    val nightFilter = float("night_filter", 0f)
    val brightness = float("brightness", -1f)
    val autoScrollSpeed = float("auto_scroll_speed", 0f)
    val hapticFeedback = bool("haptic_feedback", true)
    val markReadOnFinish = bool("mark_read_on_finish", true)

    // Browse
    val languages = strings("languages", setOf("de", "en", "all"))
    val nsfwFilter = enum("nsfw_filter", NsfwFilter.HIDE)
    val pinnedSources = strings("pinned_sources", emptySet())
    val lastUsedSource = Pref(sp, "last_source", 0L, { k, d -> getLong(k, d) }, { k, v -> putLong(k, v) })
    val repos = strings("repos", setOf(KEIYOUSHI_INDEX))
    val trustedSignatures = strings("trusted_signatures", emptySet())
    /** Entries "oldhost|https://newhost" – lets a source keep working after its site moves. */
    val domainOverrides = strings("domain_overrides", emptySet())

    // AI read-aloud (Gemini reads the page, the phone's voices speak it)
    val geminiKey = string("gemini_key", "")
    val geminiModel = string("gemini_model", "gemini-3.8-flash")
    val aiTranslateGerman = bool("ai_translate_de", true)
    val ttsRate = float("tts_rate", 1.0f)
    val voiceFemale = string("voice_female", "")
    val voiceMale = string("voice_male", "")
    val voiceNarrator = string("voice_narrator", "")
    val showCaptions = bool("ai_captions", true)

    // Fish Audio: realistic cloud voices, one per speaker type
    val useFishAudio = bool("use_fish_audio", false)
    val fishKey = string("fish_key", "")
    val fishModel = string("fish_model", "s2.1-pro")
    val fishYoungWoman = string("fish_young_woman", YOUNG_WOMAN_VOICE)
    val fishWoman = string("fish_woman", "5233336f5f44460ea0902b0802375451")
    val fishYoungMan = string("fish_young_man", YOUNG_MAN_VOICE)
    val fishMan = string("fish_man", "a0bf667d447743f0858158cd250cca39")
    val fishOldMan = string("fish_old_man", "e2d36ce651464956af0b66616e35b5cc")
    val fishNarrator = string("fish_narrator", "a0bf667d447743f0858158cd250cca39")

    // Downloads
    val downloadOnlyWifi = bool("download_only_wifi", false)
    val deleteAfterRead = bool("delete_after_read", false)

    init {
        // One-time switch to the young adult voices; the previous young-woman voice stays
        // available as a saved voice (see FishVoices), so nothing is lost.
        if (!sp.getBoolean("fish_voices_v2", false)) {
            val oldYoungWoman = sp.getString("fish_young_woman", null) ?: "98655a12fa944e26b274c535e5e03842"
            if (oldYoungWoman.isNotBlank() && oldYoungWoman != YOUNG_WOMAN_VOICE) {
                sp.edit { putString("fish_young_woman_previous", oldYoungWoman) }
            }
            fishYoungWoman.set(YOUNG_WOMAN_VOICE)
            fishYoungMan.set(YOUNG_MAN_VOICE)
            sp.edit { putBoolean("fish_voices_v2", true) }
        }
    }

    /** The young-woman voice that was set before the v2 switch, or null. Read once by FishVoices. */
    fun takePreviousYoungWoman(): String? {
        val id = sp.getString("fish_young_woman_previous", null) ?: return null
        sp.edit { remove("fish_young_woman_previous") }
        return id
    }

    companion object {
        const val YOUNG_WOMAN_VOICE = "99c26c95b60949f0abc3da00dc45d49a"
        const val YOUNG_MAN_VOICE = "d6960e37011b4568b9a1dcf776828f3e"
        const val KEIYOUSHI_INDEX = "https://github.com/keiyoushi/extensions/raw/repo/index.pb"
    }
}

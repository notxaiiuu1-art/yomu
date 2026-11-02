package app.yomu.core

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class SavedManga(
    val sourceId: Long,
    val url: String,
    val title: String,
    val thumbnail: String? = null,
    val addedAt: Long = System.currentTimeMillis(),
    // Details for the library search; filled when the comic's page is opened.
    val author: String? = null,
    val artist: String? = null,
    val description: String? = null,
    val genre: String? = null,
    /** When the details above were last loaded from the source, 0 = never. */
    val detailsAt: Long = 0,
)

@Serializable
data class Progress(
    val lastChapterUrl: String? = null,
    val lastChapterName: String? = null,
    val lastPage: Int = 0,
    val lastReadAt: Long = 0,
    val read: Set<String> = emptySet(),
    // So "Zuletzt gelesen" can show comics that aren't saved in the library.
    val title: String? = null,
    val thumbnail: String? = null,
)

@Serializable
private data class LibraryFile(
    val favorites: List<SavedManga> = emptyList(),
    val progress: Map<String, Progress> = emptyMap(),
    /** Own hashtags per comic (key = [Library.key]); kept even when a comic is removed from the library. */
    val tags: Map<String, List<String>> = emptyMap(),
)

/** Favourites and reading progress, kept in one small JSON file. */
class Library(context: Context) {

    private val file = File(context.filesDir, "library.json")
    private val json = Json { ignoreUnknownKeys = true }

    private val state = MutableStateFlow(
        runCatching { json.decodeFromString<LibraryFile>(file.readText()) }.getOrElse { LibraryFile() },
    )

    private val _favorites = MutableStateFlow(state.value.favorites)
    val favorites: StateFlow<List<SavedManga>> = _favorites.asStateFlow()

    private val _progress = MutableStateFlow(state.value.progress)
    val progress: StateFlow<Map<String, Progress>> = _progress.asStateFlow()

    private val _tags = MutableStateFlow(state.value.tags)
    val tags: StateFlow<Map<String, List<String>>> = _tags.asStateFlow()

    fun key(sourceId: Long, url: String) = "$sourceId|$url"

    fun tagsOf(sourceId: Long, url: String): List<String> = _tags.value[key(sourceId, url)].orEmpty()

    fun setTags(sourceId: Long, url: String, tags: List<String>) {
        val k = key(sourceId, url)
        _tags.update { map -> if (tags.isEmpty()) map - k else map + (k to tags) }
        save()
    }

    fun isFavorite(sourceId: Long, url: String) = _favorites.value.any { it.sourceId == sourceId && it.url == url }

    fun toggleFavorite(manga: SavedManga) {
        _favorites.update { list ->
            if (list.any { it.sourceId == manga.sourceId && it.url == manga.url }) {
                list.filterNot { it.sourceId == manga.sourceId && it.url == manga.url }
            } else {
                list + manga
            }
        }
        save()
    }

    fun updateFavorite(manga: SavedManga) {
        _favorites.update { list ->
            list.map { old ->
                if (old.sourceId == manga.sourceId && old.url == manga.url) {
                    // Never lose details we already have because one refresh came back thin.
                    manga.copy(
                        addedAt = old.addedAt,
                        author = manga.author ?: old.author,
                        artist = manga.artist ?: old.artist,
                        description = manga.description ?: old.description,
                        genre = manga.genre ?: old.genre,
                        detailsAt = maxOf(manga.detailsAt, old.detailsAt),
                    )
                } else {
                    old
                }
            }
        }
        save()
    }

    fun progressOf(sourceId: Long, url: String): Progress = _progress.value[key(sourceId, url)] ?: Progress()

    fun saveProgress(
        sourceId: Long,
        mangaUrl: String,
        chapterUrl: String,
        chapterName: String,
        page: Int,
        finished: Boolean,
        title: String? = null,
        thumbnail: String? = null,
    ) {
        val k = key(sourceId, mangaUrl)
        _progress.update { map ->
            val old = map[k] ?: Progress()
            map + (
                k to old.copy(
                    lastChapterUrl = chapterUrl,
                    lastChapterName = chapterName,
                    lastPage = page,
                    lastReadAt = System.currentTimeMillis(),
                    read = if (finished) old.read + chapterUrl else old.read,
                    title = title?.takeIf { it.isNotBlank() } ?: old.title,
                    thumbnail = thumbnail?.takeIf { it.isNotBlank() } ?: old.thumbnail,
                )
                )
        }
        save()
    }

    fun setRead(sourceId: Long, mangaUrl: String, chapterUrls: Collection<String>, read: Boolean) {
        val k = key(sourceId, mangaUrl)
        _progress.update { map ->
            val old = map[k] ?: Progress()
            map + (k to old.copy(read = if (read) old.read + chapterUrls else old.read - chapterUrls.toSet()))
        }
        save()
    }

    @Synchronized
    private fun save() {
        val data = LibraryFile(_favorites.value, _progress.value, _tags.value)
        runCatching {
            val tmp = File(file.parentFile, "library.json.tmp")
            tmp.writeText(json.encodeToString(LibraryFile.serializer(), data))
            tmp.renameTo(file)
        }
    }
}

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
)

@Serializable
data class Progress(
    val lastChapterUrl: String? = null,
    val lastChapterName: String? = null,
    val lastPage: Int = 0,
    val lastReadAt: Long = 0,
    val read: Set<String> = emptySet(),
)

@Serializable
private data class LibraryFile(
    val favorites: List<SavedManga> = emptyList(),
    val progress: Map<String, Progress> = emptyMap(),
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

    fun key(sourceId: Long, url: String) = "$sourceId|$url"

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
            list.map { if (it.sourceId == manga.sourceId && it.url == manga.url) manga.copy(addedAt = it.addedAt) else it }
        }
        save()
    }

    fun progressOf(sourceId: Long, url: String): Progress = _progress.value[key(sourceId, url)] ?: Progress()

    fun saveProgress(sourceId: Long, mangaUrl: String, chapterUrl: String, chapterName: String, page: Int, finished: Boolean) {
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
        val data = LibraryFile(_favorites.value, _progress.value)
        runCatching {
            val tmp = File(file.parentFile, "library.json.tmp")
            tmp.writeText(json.encodeToString(LibraryFile.serializer(), data))
            tmp.renameTo(file)
        }
    }
}

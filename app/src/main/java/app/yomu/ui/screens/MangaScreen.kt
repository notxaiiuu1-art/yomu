package app.yomu.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.InputChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.runtime.remember
import androidx.compose.ui.text.input.ImeAction
import app.yomu.core.LibrarySearch
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.yomu.Yomu
import app.yomu.core.SavedManga
import app.yomu.ui.CoverImage
import app.yomu.ui.ErrorBox
import app.yomu.ui.LocalNavigator
import app.yomu.ui.Screen
import app.yomu.ui.WebViewActivity
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

/**
 * @param resume open the reader at the last read chapter as soon as the chapters are loaded
 *   (used by "Zuletzt gelesen").
 */
class MangaScreen(private val sourceId: Long, initial: SManga, resume: Boolean = false) : Screen() {

    private val source = Yomu.extensions.source(sourceId)
    private var manga by mutableStateOf(initial)
    private var chapters by mutableStateOf<List<SChapter>>(emptyList())
    private var loading by mutableStateOf(true)
    private var error by mutableStateOf<String?>(null)
    private var descriptionExpanded by mutableStateOf(false)
    private var detailsLoaded = false
    private var tagDialog by mutableStateOf(false)
    private var pendingResume = resume
    /** Lives with the screen, so coming back from the reader keeps the chapter list where it was. */
    private val listState = LazyListState()

    init {
        refresh()
    }

    private fun refresh() {
        val src = source ?: run {
            loading = false
            error = "Die Quelle dieses Mangas ist nicht installiert."
            return
        }
        loading = true
        error = null
        scope.launch {
            try {
                val update = withContext(Dispatchers.IO) {
                    src.getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = true)
                }
                // Keep the url and title we came with if the source returns blanks.
                val details = update.manga
                if (details.url.isBlank()) details.url = manga.url
                if (details.title.isBlank()) details.title = manga.title
                if (details.thumbnail_url.isNullOrBlank()) details.thumbnail_url = manga.thumbnail_url
                manga = details
                chapters = update.chapters
                detailsLoaded = true
                if (Yomu.library.isFavorite(sourceId, manga.url)) {
                    Yomu.library.updateFavorite(saved())
                }
            } catch (e: Throwable) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                error = e.message ?: e.javaClass.simpleName
            } finally {
                loading = false
            }
        }
    }

    private fun saved() = SavedManga(
        sourceId = sourceId,
        url = manga.url,
        title = manga.title,
        thumbnail = manga.thumbnail_url,
        author = manga.author?.takeIf { it.isNotBlank() },
        artist = manga.artist?.takeIf { it.isNotBlank() },
        description = manga.description?.takeIf { it.isNotBlank() },
        genre = manga.genre?.takeIf { it.isNotBlank() },
        detailsAt = if (detailsLoaded) System.currentTimeMillis() else 0L,
    )

    private fun open(chapter: SChapter) {
        val src = source as? HttpSource ?: return
        // Reader works oldest → newest; sources list newest first.
        val ordered = chapters.reversed()
        val index = ordered.indexOfFirst { it.url == chapter.url }.coerceAtLeast(0)
        val progress = Yomu.library.progressOf(sourceId, manga.url)
        val startPage = if (progress.lastChapterUrl == chapter.url) progress.lastPage else 0
        navigatorRef?.push(ReaderScreen(src, manga, ordered, index, startPage))
    }

    private var navigatorRef: app.yomu.ui.Navigator? = null

    @Composable
    override fun Content() {
        val nav = LocalNavigator.current
        navigatorRef = nav
        val context = LocalContext.current
        val favorites by Yomu.library.favorites.collectAsState()
        val progressMap by Yomu.library.progress.collectAsState()
        val isFavorite = favorites.any { it.sourceId == sourceId && it.url == manga.url }
        val progress = progressMap[Yomu.library.key(sourceId, manga.url)]
        val readSet = progress?.read.orEmpty()
        val allTags by Yomu.library.tags.collectAsState()
        val ownTags = allTags[Yomu.library.key(sourceId, manga.url)].orEmpty()

        if (tagDialog) {
            HashtagDialog(
                onDismiss = { tagDialog = false },
                onAdd = { added ->
                    val merged = (ownTags + added).distinctBy { LibrarySearch.tagKey(it) }
                    Yomu.library.setTags(sourceId, manga.url, merged)
                    tagDialog = false
                },
            )
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {},
                    navigationIcon = {
                        IconButton(onClick = { nav.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") }
                    },
                    actions = {
                        IconButton(onClick = ::refresh) { Icon(Icons.Filled.Refresh, "Aktualisieren") }
                        (source as? HttpSource)?.let { http ->
                            TextButton(onClick = {
                                val url = app.yomu.core.Domains.rewrite(
                                    runCatching { http.getMangaUrl(manga) }.getOrNull() ?: http.baseUrl,
                                )
                                context.startActivity(WebViewActivity.intent(context, url))
                            }) { Text("Web") }
                        }
                    },
                )
            },
        ) { padding ->
            LaunchedEffect(chapters) {
                if (pendingResume && chapters.isNotEmpty()) {
                    pendingResume = false
                    val last = Yomu.library.progressOf(sourceId, manga.url).lastChapterUrl
                    chapters.firstOrNull { it.url == last }?.let(::open)
                }
            }
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                state = listState,
                contentPadding = PaddingValues(bottom = 32.dp),
            ) {
                item {
                    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        CoverImage(
                            manga.thumbnail_url,
                            source,
                            Modifier.width(110.dp).height(165.dp).clip(RoundedCornerShape(12.dp)),
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(manga.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            listOfNotNull(manga.author, manga.artist).distinct().filter { it.isNotBlank() }
                                .takeIf { it.isNotEmpty() }
                                ?.let { Text(it.joinToString(" · "), style = MaterialTheme.typography.bodyMedium) }
                            Text(
                                "${source?.name ?: "?"} · ${statusLabel(manga.status)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        val resume = progress?.lastChapterUrl?.let { url -> chapters.firstOrNull { it.url == url } }
                        val first = chapters.lastOrNull()
                        Button(
                            onClick = { (resume ?: first)?.let(::open) },
                            enabled = chapters.isNotEmpty(),
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Filled.PlayArrow, null)
                            Text(if (resume != null) "Weiterlesen" else "Lesen", Modifier.padding(start = 6.dp))
                        }
                        Button(
                            onClick = { Yomu.library.toggleFavorite(saved()) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, null)
                            Text(if (isFavorite) "Gespeichert" else "Speichern", Modifier.padding(start = 6.dp))
                        }
                    }
                }
                manga.description?.takeIf { it.isNotBlank() }?.let { desc ->
                    item {
                        Text(
                            desc.trim(),
                            maxLines = if (descriptionExpanded) Int.MAX_VALUE else 4,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .padding(16.dp)
                                .animateContentSize()
                                .clickable { descriptionExpanded = !descriptionExpanded },
                        )
                    }
                }
                item {
                    // Genres from the source and own hashtags; tapping one searches the library.
                    val genres = LibrarySearch.splitGenres(manga.genre)
                    val ownKeys = ownTags.map { LibrarySearch.tagKey(it) }.toSet()
                    FlowRow(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        genres.filter { LibrarySearch.tagKey(it) !in ownKeys }.forEach { tag ->
                            SuggestionChip(
                                onClick = { nav.push(LibrarySearchScreen(hashtagQuery(tag))) },
                                label = { Text("#$tag") },
                            )
                        }
                        ownTags.forEach { tag ->
                            InputChip(
                                selected = true,
                                onClick = { nav.push(LibrarySearchScreen(hashtagQuery(tag))) },
                                label = { Text("#$tag") },
                                trailingIcon = {
                                    Icon(
                                        Icons.Filled.Close,
                                        "Hashtag entfernen",
                                        Modifier.size(18.dp).clickable {
                                            Yomu.library.setTags(sourceId, manga.url, ownTags - tag)
                                        },
                                    )
                                },
                            )
                        }
                        AssistChip(
                            onClick = { tagDialog = true },
                            label = { Text("Hashtag") },
                            leadingIcon = { Icon(Icons.Filled.Add, null, Modifier.size(18.dp)) },
                        )
                    }
                }
                item {
                    Text(
                        if (loading) "Kapitel werden geladen …" else "${chapters.size} Kapitel",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp),
                    )
                    HorizontalDivider()
                }
                when {
                    loading && chapters.isEmpty() -> item {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    error != null && chapters.isEmpty() -> item {
                        Box(Modifier.height(260.dp)) {
                            ErrorBox(error!!, onRetry = ::refresh, webUrl = (source as? HttpSource)?.baseUrl)
                        }
                    }
                    else -> items(chapters) { chapter ->
                        val read = chapter.url in readSet
                        val current = chapter.url == progress?.lastChapterUrl
                        ListItem(
                            headlineContent = {
                                Text(
                                    chapter.name,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
                                )
                            },
                            supportingContent = {
                                val parts = listOfNotNull(
                                    chapter.date_upload.takeIf { it > 0 }?.let {
                                        DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it))
                                    },
                                    chapter.scanlator?.takeIf { it.isNotBlank() },
                                    if (current && progress != null) "Seite ${progress.lastPage + 1}" else null,
                                )
                                if (parts.isNotEmpty()) Text(parts.joinToString(" · "), maxLines = 1)
                            },
                            trailingContent = {
                                IconButton(onClick = {
                                    Yomu.library.setRead(sourceId, manga.url, listOf(chapter.url), !read)
                                }) {
                                    Icon(
                                        Icons.Filled.Check,
                                        if (read) "Als ungelesen markieren" else "Als gelesen markieren",
                                        tint = if (read) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                    )
                                }
                            },
                            colors = ListItemDefaults.colors(),
                            modifier = Modifier
                                .alpha(if (read && !current) 0.5f else 1f)
                                .clickable { open(chapter) },
                        )
                    }
                }
            }
        }
    }
}

/** "Slice of Life" → "#SliceofLife ", ready to search. */
private fun hashtagQuery(tag: String) = "#" + tag.filter { !it.isWhitespace() } + " "

@Composable
private fun HashtagDialog(onDismiss: () -> Unit, onAdd: (List<String>) -> Unit) {
    var text by remember { mutableStateOf("") }
    val tags = LibrarySearch.splitUserTags(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Eigene Hashtags") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("z. B. fun lieblings spannend") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (tags.isNotEmpty()) onAdd(tags) }),
                )
                Text(
                    if (tags.isEmpty()) "Mehrere mit Leerzeichen trennen." else tags.joinToString("  ") { "#$it" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(tags) }, enabled = tags.isNotEmpty()) { Text("Hinzufügen") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

private fun statusLabel(status: Int) = when (status) {
    SManga.ONGOING -> "Laufend"
    SManga.COMPLETED -> "Abgeschlossen"
    SManga.LICENSED -> "Lizenziert"
    SManga.PUBLISHING_FINISHED -> "Veröffentlichung beendet"
    SManga.CANCELLED -> "Abgebrochen"
    SManga.ON_HIATUS -> "Pausiert"
    else -> "Status unbekannt"
}

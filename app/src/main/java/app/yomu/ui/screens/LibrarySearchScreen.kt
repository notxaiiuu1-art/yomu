package app.yomu.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.yomu.Yomu
import app.yomu.core.ComicText
import app.yomu.core.LibrarySearch
import app.yomu.core.LibrarySearch.Mode
import app.yomu.core.SavedManga
import app.yomu.core.SearchDoc
import app.yomu.ui.CoverImage
import app.yomu.ui.LocalNavigator
import app.yomu.ui.Screen
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * Searches everything saved in the library, offline: `#tag` for hashtags, plain words by
 * name or in the story (description, genres, and what read-aloud found inside the comic).
 */
class LibrarySearchScreen(initialQuery: String = "") : Screen() {

    private var field by mutableStateOf(TextFieldValue(initialQuery, TextRange(initialQuery.length)))
    private var mode by mutableStateOf(Mode.NAME)
    /** Opens the keyboard once; not again when coming back from a comic. */
    private var autoFocus = initialQuery.isEmpty()

    /** Text inside each comic, by library key; loaded from disk once. */
    private var comicText by mutableStateOf<Map<String, List<String>>>(emptyMap())

    /** Saved comics whose description and genres are still being fetched. */
    private var fetching by mutableIntStateOf(0)

    /** Lives with the screen, so coming back from a comic keeps the scroll position. */
    private var listState by mutableStateOf(LazyListState())
    private var listedQuery: LibrarySearch.Query? = null
    private var listedMode = Mode.NAME
    private var nameHits by mutableStateOf<List<LibrarySearch.Hit>>(emptyList())
    private var storyHits by mutableStateOf<List<LibrarySearch.Hit>>(emptyList())
    /** The query the hits above belong to; "no hits" is only shown once they're for the current one. */
    private var hitsFor by mutableStateOf<LibrarySearch.Query?>(null)

    init {
        scope.launch {
            comicText = withContext(Dispatchers.IO) {
                Yomu.library.favorites.value.associate { key(it) to ComicText.read(key(it)) }
                    .filterValues { it.isNotEmpty() }
            }
        }
        fetchMissingDetails()
    }

    private fun key(m: SavedManga) = Yomu.library.key(m.sourceId, m.url)

    /**
     * Comics saved before the search existed have no description or genres stored yet. Fetch
     * them once in the background (details only, no chapter lists), a few at a time.
     */
    private fun fetchMissingDetails() {
        val missing = Yomu.library.favorites.value.filter { it.detailsAt == 0L && it.description == null && it.genre == null }
        if (missing.isEmpty()) return
        val limit = Semaphore(3)
        fetching = missing.size
        missing.forEach { saved ->
            scope.launch {
                try {
                    val source = Yomu.extensions.source(saved.sourceId) ?: return@launch
                    val details = limit.withPermit {
                        withContext(Dispatchers.IO) {
                            val manga = SManga.create().apply {
                                url = saved.url
                                title = saved.title
                                thumbnail_url = saved.thumbnail
                            }
                            source.getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
                        }
                    }
                    Yomu.library.updateFavorite(
                        saved.copy(
                            author = details.author?.takeIf { it.isNotBlank() },
                            artist = details.artist?.takeIf { it.isNotBlank() },
                            description = details.description?.takeIf { it.isNotBlank() },
                            genre = details.genre?.takeIf { it.isNotBlank() },
                            detailsAt = System.currentTimeMillis(),
                        ),
                    )
                } catch (e: Throwable) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    // Offline or the site is down: try again next time.
                } finally {
                    fetching--
                }
            }
        }
    }

    /** Replaces the word being typed (the last one) with [word], e.g. a tapped hashtag. */
    private fun completeLastWord(word: String) {
        val text = field.text
        val start = text.lastIndexOfAny(charArrayOf(' ', '\n')) + 1
        val updated = text.substring(0, start) + word + " "
        field = TextFieldValue(updated, TextRange(updated.length))
    }

    private fun addTag(tag: String) {
        val text = field.text.trimEnd()
        val updated = (if (text.isEmpty()) "" else "$text ") + "#" + tag.filter { !it.isWhitespace() } + " "
        field = TextFieldValue(updated, TextRange(updated.length))
    }

    @Composable
    override fun Content() {
        val nav = LocalNavigator.current
        val focus = remember { FocusRequester() }
        val focusManager = LocalFocusManager.current
        val favorites by Yomu.library.favorites.collectAsState()
        val tags by Yomu.library.tags.collectAsState()
        val installed by Yomu.extensions.installed.collectAsState()

        val docs = remember(favorites, tags, comicText) {
            favorites.map { m ->
                val k = key(m)
                SearchDoc(
                    key = k,
                    title = m.title,
                    author = listOfNotNull(m.author, m.artist).filter { it.isNotBlank() }.distinct().joinToString(", ")
                        .ifBlank { null },
                    description = m.description,
                    genres = LibrarySearch.splitGenres(m.genre),
                    userTags = tags[k].orEmpty(),
                    comicText = comicText[k].orEmpty(),
                )
            }
        }
        val byKey = remember(favorites) { favorites.associateBy { key(it) } }

        val query = remember(field.text) { LibrarySearch.parse(field.text) }
        // The word under the cursor at the end, if it is a hashtag being typed.
        val typingTag = field.text.takeIf { !it.endsWith(" ") }?.substringAfterLast(' ')?.takeIf { it.startsWith("#") }

        // Both modes are counted, so the chips can show where the hits are.
        LaunchedEffect(query, docs) {
            delay(80) // let fast typing settle
            val (n, s) = withContext(Dispatchers.Default) {
                LibrarySearch.search(docs, query, Mode.NAME) to
                    if (query.text.isBlank()) emptyList() else LibrarySearch.search(docs, query, Mode.STORY)
            }
            nameHits = n
            storyHits = s
            hitsFor = query
        }
        // A different search starts at the top; coming back to the same one keeps the position.
        LaunchedEffect(query, mode) {
            if (listedQuery != query || listedMode != mode) {
                if (listedQuery != null) listState = LazyListState()
                listedQuery = query
                listedMode = mode
            }
        }
        val hits = if (query.text.isBlank() || mode == Mode.NAME) nameHits else storyHits
        val otherHits = if (mode == Mode.NAME) storyHits else nameHits

        LaunchedEffect(Unit) {
            if (autoFocus) {
                autoFocus = false
                runCatching { focus.requestFocus() }
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Bibliothek durchsuchen") },
                    navigationIcon = {
                        IconButton(onClick = { nav.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") }
                    },
                )
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                OutlinedTextField(
                    value = field,
                    onValueChange = { field = it },
                    placeholder = { Text("Name, Wort aus der Story oder #Hashtag") },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    trailingIcon = {
                        if (field.text.isNotEmpty()) {
                            IconButton(onClick = { field = TextFieldValue("") }) { Icon(Icons.Filled.Close, "Leeren") }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .focusRequester(focus),
                )

                if (query.text.isNotBlank()) {
                    Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = mode == Mode.NAME,
                            onClick = { mode = Mode.NAME },
                            label = { Text("Name · ${nameHits.size}") },
                        )
                        FilterChip(
                            selected = mode == Mode.STORY,
                            onClick = { mode = Mode.STORY },
                            label = { Text("Story · ${storyHits.size}") },
                        )
                    }
                }

                if (fetching > 0) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        Text(
                            "Lade Beschreibungen & Hashtags für $fetching gespeicherte Comics …",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 4.dp))
                    }
                }

                // Hashtag suggestions: while typing "#…", or all hashtags when nothing is typed yet.
                val suggestions = remember(docs, typingTag, query.isEmpty) {
                    when {
                        typingTag != null -> LibrarySearch.suggestTags(docs, typingTag.removePrefix("#")).take(30)
                        query.isEmpty -> LibrarySearch.allTags(docs).take(40)
                        else -> emptyList()
                    }
                }
                if (suggestions.isNotEmpty()) {
                    FlowRow(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        suggestions.forEach { s ->
                            AssistChip(
                                onClick = {
                                    val word = "#" + s.tag.filter { !it.isWhitespace() }
                                    if (typingTag != null) completeLastWord(word) else addTag(s.tag)
                                },
                                label = { Text("#${s.tag} · ${s.count}") },
                            )
                        }
                    }
                }

                when {
                    favorites.isEmpty() -> Hint("Deine Bibliothek ist noch leer.\nSpeichere Comics mit ♥, dann kannst du sie hier durchsuchen.")
                    query.isEmpty -> Hint(
                        "Tippe einen Namen, um nach Titeln zu suchen, oder wähle „Story“, um in Beschreibung und " +
                            "Comic-Text zu suchen.\n\n#Hashtag findet Comics mit diesem Genre oder deinem eigenen Hashtag – " +
                            "auch verwandte: #fun findet „Comedy“.",
                    )
                    hits.isEmpty() && hitsFor != query -> Unit // still searching (takes a moment)
                    hits.isEmpty() -> Column(
                        Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Keine Treffer", style = MaterialTheme.typography.titleMedium)
                        if (query.text.isNotBlank() && otherHits.isNotEmpty()) {
                            TextButton(onClick = { mode = if (mode == Mode.NAME) Mode.STORY else Mode.NAME }) {
                                Text("${otherHits.size} Treffer in „${if (mode == Mode.NAME) "Story" else "Name"}“ anzeigen")
                            }
                        }
                        if (query.text.isBlank() && query.tags.isNotEmpty()) {
                            val words = query.tags.joinToString(" ")
                            TextButton(onClick = {
                                field = TextFieldValue(words, TextRange(words.length))
                                mode = Mode.STORY
                            }) { Text("„$words“ stattdessen in der Story suchen") }
                        }
                    }
                    else -> LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 24.dp)) {
                        items(hits, key = { it.doc.key }) { hit ->
                            val saved = byKey[hit.doc.key] ?: return@items
                            val source = installed.firstNotNullOfOrNull { ext -> ext.sources.firstOrNull { it.id == saved.sourceId } }
                            ResultRow(hit, saved, source) {
                                val manga = SManga.create().apply {
                                    url = saved.url
                                    title = saved.title
                                    thumbnail_url = saved.thumbnail
                                    author = saved.author
                                    artist = saved.artist
                                    description = saved.description
                                    genre = saved.genre
                                }
                                nav.push(MangaScreen(saved.sourceId, manga))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(24.dp),
    )
}

@Composable
private fun ResultRow(
    hit: LibrarySearch.Hit,
    saved: SavedManga,
    source: eu.kanade.tachiyomi.source.Source?,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CoverImage(saved.thumbnail, source, Modifier.width(52.dp).height(78.dp).clip(RoundedCornerShape(8.dp)))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            val title = if (hit.where == LibrarySearch.Where.TITLE && hit.snippet != null) {
                highlighted(hit.snippet)
            } else {
                AnnotatedString(saved.title)
            }
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (hit.matchedTags.isNotEmpty()) {
                Text(
                    hit.matchedTags.joinToString("  ") { "#$it" },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val where = hit.where
            if (where != null && where != LibrarySearch.Where.TITLE && where != LibrarySearch.Where.TAG) {
                Text(
                    where.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                hit.snippet?.let {
                    Text(highlighted(it), style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun highlighted(snippet: LibrarySearch.Snippet): AnnotatedString {
    val color = MaterialTheme.colorScheme.primary
    return buildAnnotatedString {
        append(snippet.text)
        snippet.highlights.forEach { r ->
            if (r.first >= 0 && r.last < snippet.text.length) {
                addStyle(SpanStyle(fontWeight = FontWeight.Bold, color = color), r.first, r.last + 1)
            }
        }
    }
}

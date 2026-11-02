package app.yomu.ui.screens

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.remember
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.yomu.Yomu
import app.yomu.ui.LocalNavigator
import app.yomu.ui.MangaCard
import app.yomu.ui.Screen
import eu.kanade.tachiyomi.source.model.SManga

class HomeScreen : Screen() {

    private var tab by mutableIntStateOf(0)
    private val browse = BrowseTab()
    private val extensions = ExtensionsTab()
    // Scroll positions live here, so they survive opening a comic and switching tabs.
    private val libraryGrid = LazyGridState()
    private val settingsScroll = ScrollState(0)

    @Composable
    override fun Content() {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    val items = listOf(
                        "Bibliothek" to Icons.Filled.Favorite,
                        "Entdecken" to Icons.Filled.Search,
                        "Erweiterungen" to Icons.Filled.Build,
                        "Einstellungen" to Icons.Filled.Settings,
                    )
                    items.forEachIndexed { index, (label, icon) ->
                        NavigationBarItem(
                            selected = tab == index,
                            onClick = { tab = index },
                            icon = { Icon(icon, null) },
                            label = { Text(label, maxLines = 1) },
                        )
                    }
                }
            },
        ) { padding ->
            Box(Modifier.padding(padding)) {
                when (tab) {
                    0 -> LibraryTab(onBrowse = { tab = 1 }, gridState = libraryGrid)
                    1 -> browse.Content(scope)
                    2 -> extensions.Content(scope)
                    else -> SettingsTab(settingsScroll)
                }
            }
        }
    }
}

/** A comic from the reading history, newest first. */
private class RecentRead(
    val sourceId: Long,
    val url: String,
    val title: String,
    val thumbnail: String?,
    val chapterName: String?,
    val page: Int,
)

@Composable
private fun LibraryTab(onBrowse: () -> Unit, gridState: LazyGridState) {
    val nav = LocalNavigator.current
    val favorites by Yomu.library.favorites.collectAsState()
    val progress by Yomu.library.progress.collectAsState()
    val installed by Yomu.extensions.installed.collectAsState()
    val columns by Yomu.prefs.gridColumns.flow.collectAsState()

    val sorted = favorites.sortedByDescending {
        maxOf(progress[Yomu.library.key(it.sourceId, it.url)]?.lastReadAt ?: 0L, it.addedAt)
    }

    // Last read comics, saved or not. Older history entries only have a title if the comic is saved.
    val recent = remember(progress, favorites) {
        val byKey = favorites.associateBy { Yomu.library.key(it.sourceId, it.url) }
        progress.entries
            .filter { it.value.lastReadAt > 0 && it.value.lastChapterUrl != null }
            .sortedByDescending { it.value.lastReadAt }
            .mapNotNull { (key, p) ->
                val sourceId = key.substringBefore('|').toLongOrNull() ?: return@mapNotNull null
                val url = key.substringAfter('|')
                val saved = byKey[key]
                val title = p.title ?: saved?.title ?: return@mapNotNull null
                RecentRead(sourceId, url, title, p.thumbnail ?: saved?.thumbnail, p.lastChapterName, p.lastPage)
            }
            .take(20)
    }

    fun sourceOf(id: Long) = installed.firstNotNullOfOrNull { ext -> ext.sources.firstOrNull { it.id == id } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bibliothek") },
                actions = {
                    if (favorites.isNotEmpty()) {
                        IconButton(onClick = { nav.push(LibrarySearchScreen()) }) {
                            Icon(Icons.Filled.Search, "Bibliothek durchsuchen")
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (sorted.isEmpty() && recent.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(padding).padding(32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "Noch nichts gespeichert.\n\n1. Unter „Erweiterungen“ eine Quelle installieren\n2. Unter „Entdecken“ einen Manga suchen\n3. Mit ♥ zur Bibliothek hinzufügen",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Button(onClick = onBrowse) { Text("Manga entdecken") }
            }
            return@Scaffold
        }
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(columns),
            contentPadding = PaddingValues(12.dp, 12.dp, 12.dp, 24.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(padding),
        ) {
            if (recent.isNotEmpty()) {
                item(key = "recent", span = { GridItemSpan(maxLineSpan) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Zuletzt gelesen", style = MaterialTheme.typography.titleMedium)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(recent, key = { "${it.sourceId}|${it.url}" }) { r ->
                                Column(Modifier.width(112.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    // Tapping continues reading right where you stopped.
                                    MangaCard(title = r.title, cover = r.thumbnail, source = sourceOf(r.sourceId), badge = "▶") {
                                        val manga = SManga.create().apply {
                                            url = r.url
                                            title = r.title
                                            thumbnail_url = r.thumbnail
                                        }
                                        nav.push(MangaScreen(r.sourceId, manga, resume = true))
                                    }
                                    Text(
                                        listOfNotNull(r.chapterName, "S. ${r.page + 1}").joinToString(" · "),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                        if (sorted.isNotEmpty()) {
                            Text(
                                "Gespeichert",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }
            }
            items(sorted, key = { "${it.sourceId}|${it.url}" }) { saved ->
                val p = progress[Yomu.library.key(saved.sourceId, saved.url)]
                MangaCard(
                    title = saved.title,
                    cover = saved.thumbnail,
                    source = sourceOf(saved.sourceId),
                    badge = p?.lastChapterName?.let { "▶" },
                    onClick = {
                        val manga = SManga.create().apply {
                            url = saved.url
                            title = saved.title
                            thumbnail_url = saved.thumbnail
                        }
                        nav.push(MangaScreen(saved.sourceId, manga))
                    },
                )
            }
        }
    }
}

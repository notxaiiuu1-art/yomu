package app.yomu.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
                    0 -> LibraryTab(onBrowse = { tab = 1 })
                    1 -> browse.Content(scope)
                    2 -> extensions.Content(scope)
                    else -> SettingsTab()
                }
            }
        }
    }
}

@Composable
private fun LibraryTab(onBrowse: () -> Unit) {
    val nav = LocalNavigator.current
    val favorites by Yomu.library.favorites.collectAsState()
    val progress by Yomu.library.progress.collectAsState()
    val installed by Yomu.extensions.installed.collectAsState()
    val columns by Yomu.prefs.gridColumns.flow.collectAsState()

    val sorted = favorites.sortedByDescending {
        maxOf(progress[Yomu.library.key(it.sourceId, it.url)]?.lastReadAt ?: 0L, it.addedAt)
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Bibliothek") }) }) { padding ->
        if (sorted.isEmpty()) {
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
            columns = GridCells.Fixed(columns),
            contentPadding = PaddingValues(12.dp, 12.dp, 12.dp, 24.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(padding),
        ) {
            items(sorted, key = { "${it.sourceId}|${it.url}" }) { saved ->
                val source = installed.firstNotNullOfOrNull { ext -> ext.sources.firstOrNull { it.id == saved.sourceId } }
                val p = progress[Yomu.library.key(saved.sourceId, saved.url)]
                MangaCard(
                    title = saved.title,
                    cover = saved.thumbnail,
                    source = source,
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

package app.yomu.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.yomu.Yomu
import app.yomu.ui.LocalNavigator
import app.yomu.ui.MangaCard
import app.yomu.ui.Screen
import app.yomu.ui.langName
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

private sealed interface SearchResult {
    data object Loading : SearchResult
    data class Done(val mangas: List<SManga>) : SearchResult
    data class Failed(val message: String) : SearchResult
}

/** Searches every installed source in parallel; results appear as each source answers. */
class GlobalSearchScreen(private val query: String, sourceIds: List<Long>) : Screen() {

    private val sources = sourceIds.mapNotNull { Yomu.extensions.source(it) }
    private val results = mutableStateMapOf<Long, SearchResult>()

    init {
        val limit = Semaphore(6)
        sources.forEach { source ->
            results[source.id] = SearchResult.Loading
            scope.launch {
                val result = try {
                    limit.withPermit {
                        withContext(Dispatchers.IO) {
                            SearchResult.Done(source.getSearchManga(1, query, source.getFilterList()).mangas)
                        }
                    }
                } catch (e: Throwable) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    SearchResult.Failed(e.message ?: e.javaClass.simpleName)
                }
                results[source.id] = result
            }
        }
    }

    @Composable
    override fun Content() {
        val nav = LocalNavigator.current
        // Sources with hits first, then still loading, then empty or failed.
        val ordered = sources.sortedBy {
            when (val r = results[it.id]) {
                is SearchResult.Done -> if (r.mangas.isNotEmpty()) 0 else 3
                SearchResult.Loading, null -> 1
                is SearchResult.Failed -> 2
            }
        }
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("„$query“") },
                    navigationIcon = {
                        IconButton(onClick = { nav.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") }
                    },
                )
            },
        ) { padding ->
            LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(ordered, key = { it.id }) { source ->
                    val result = results[source.id]
                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { nav.push(SourceScreen(source.id)) }
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(source.name, style = MaterialTheme.typography.titleMedium)
                            Text(langName(source.lang), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        when (result) {
                            SearchResult.Loading, null ->
                                CircularProgressIndicator(Modifier.padding(16.dp).size(24.dp), strokeWidth = 2.dp)
                            is SearchResult.Failed -> Text(
                                result.message,
                                color = MaterialTheme.colorScheme.error,
                                maxLines = 2,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                            is SearchResult.Done -> if (result.mangas.isEmpty()) {
                                Text(
                                    "Keine Treffer",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                )
                            } else {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    items(result.mangas) { manga ->
                                        Column(Modifier.width(120.dp)) {
                                            MangaCard(manga.title, manga.thumbnail_url, source) {
                                                nav.push(MangaScreen(source.id, manga))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

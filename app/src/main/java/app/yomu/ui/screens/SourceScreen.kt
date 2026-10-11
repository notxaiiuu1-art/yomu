package app.yomu.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.yomu.Yomu
import app.yomu.ui.ErrorBox
import app.yomu.ui.LocalNavigator
import app.yomu.ui.MangaCard
import app.yomu.ui.Screen
import app.yomu.ui.WebViewActivity
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private enum class Listing { POPULAR, LATEST, SEARCH }

class SourceScreen(private val sourceId: Long) : Screen() {

    private val source: CatalogueSource? = Yomu.extensions.source(sourceId)

    private var listing by mutableStateOf(Listing.POPULAR)
    private var query by mutableStateOf("")
    private val items = mutableStateListOf<SManga>()
    private var page = 0
    private var hasNext by mutableStateOf(true)
    private var loading by mutableStateOf(false)
    private var error by mutableStateOf<String?>(null)
    private var job: Job? = null

    init {
        Yomu.prefs.lastUsedSource.set(sourceId)
        loadMore()
    }

    private fun reset(newListing: Listing) {
        job?.cancel()
        listing = newListing
        items.clear()
        page = 0
        hasNext = true
        error = null
        loading = false
        loadMore()
    }

    private fun loadMore() {
        val src = source ?: return
        if (loading || !hasNext) return
        loading = true
        error = null
        job = scope.launch {
            try {
                val next = page + 1
                val result = when (listing) {
                    Listing.POPULAR -> src.getPopularManga(next)
                    Listing.LATEST -> src.getLatestUpdates(next)
                    Listing.SEARCH -> src.getSearchManga(next, query, src.getFilterList())
                }
                val known = items.map { it.url }.toHashSet()
                items.addAll(result.mangas.filter { known.add(it.url) })
                page = next
                hasNext = result.hasNextPage && result.mangas.isNotEmpty()
            } catch (e: Throwable) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                error = e.message ?: e.javaClass.simpleName
            } finally {
                loading = false
            }
        }
    }

    @Composable
    override fun Content() {
        val nav = LocalNavigator.current
        val context = LocalContext.current
        val columns by Yomu.prefs.gridColumns.flow.collectAsState()
        val src = source

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(src?.name ?: "Quelle fehlt") },
                    navigationIcon = {
                        IconButton(onClick = { nav.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") }
                    },
                    actions = {
                        if (src is HttpSource) {
                            TextButton(onClick = { context.startActivity(WebViewActivity.intent(context, src.baseUrl)) }) {
                                Text("Web")
                            }
                        }
                    },
                )
            },
        ) { padding ->
            if (src == null) {
                ErrorBox("Diese Quelle ist nicht installiert.", modifier = Modifier.padding(padding))
                return@Scaffold
            }
            Column(Modifier.fillMaxSize().padding(padding)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("In ${src.name} suchen …") },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { if (query.isNotBlank()) reset(Listing.SEARCH) }),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                )
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = listing == Listing.POPULAR,
                        onClick = { reset(Listing.POPULAR) },
                        label = { Text("Beliebt") },
                    )
                    if (src.supportsLatest) {
                        FilterChip(
                            selected = listing == Listing.LATEST,
                            onClick = { reset(Listing.LATEST) },
                            label = { Text("Neu") },
                        )
                    }
                }

                if (items.isEmpty() && error != null) {
                    ErrorBox(
                        error!!,
                        onRetry = { hasNext = true; loadMore() },
                        webUrl = (src as? HttpSource)?.baseUrl,
                    )
                    return@Column
                }
                if (items.isEmpty() && loading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    return@Column
                }

                val gridState = rememberLazyGridState()
                val nearEnd by remember {
                    derivedStateOf {
                        val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                        last >= gridState.layoutInfo.totalItemsCount - 6
                    }
                }
                LaunchedEffect(nearEnd, items.size) { if (nearEnd) loadMore() }

                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(columns),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    itemsIndexed(items, key = { _, m -> m.url }) { _, manga ->
                        MangaCard(
                            title = manga.title,
                            cover = manga.thumbnail_url,
                            source = src,
                            badge = if (Yomu.library.isFavorite(sourceId, manga.url)) "♥" else null,
                            onClick = { nav.push(MangaScreen(sourceId, manga)) },
                        )
                    }
                    if (loading || error != null) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                if (loading) CircularProgressIndicator() else TextButton(onClick = { loadMore() }) {
                                    Text("Fehler – nochmal laden")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

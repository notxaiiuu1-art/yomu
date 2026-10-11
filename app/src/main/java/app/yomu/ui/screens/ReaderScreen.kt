package app.yomu.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.yomu.Yomu
import app.yomu.core.Images
import app.yomu.core.ReaderMode
import app.yomu.ui.LocalNavigator
import app.yomu.ui.MainActivity
import app.yomu.ui.Screen
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ReaderScreen(
    private val source: HttpSource,
    private val manga: SManga,
    private val chapters: List<SChapter>,
    startIndex: Int,
    private val startPage: Int,
) : Screen() {

    override val immersive = true

    private var index by mutableIntStateOf(startIndex)
    private var pages by mutableStateOf<List<Page>>(emptyList())
    private var loading by mutableStateOf(true)
    private var error by mutableStateOf<String?>(null)
    private var menu by mutableStateOf(false)
    private var mode by mutableStateOf(Yomu.prefs.readerMode.get())
    private var current by mutableIntStateOf(0)
    private var initialPage = startPage
    private var preloadJob: Job? = null
    private var reloadKey by mutableIntStateOf(0)

    private val chapter get() = chapters[index]

    init {
        load()
    }

    private fun load() {
        loading = true
        error = null
        pages = emptyList()
        scope.launch {
            try {
                val list = withContext(Dispatchers.IO) { source.getPageList(chapter) }
                check(list.isNotEmpty()) { "Dieses Kapitel hat keine Seiten" }
                pages = list
                current = initialPage.coerceIn(0, list.lastIndex)
                if (mode == ReaderMode.AUTO) mode = guessMode(list)
                preload(current)
            } catch (e: Throwable) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                error = e.message ?: e.javaClass.simpleName
            } finally {
                loading = false
            }
        }
    }

    /** Long-strip comics (manhwa / webtoons) read best vertically; everything else as pages. */
    private suspend fun guessMode(list: List<Page>): ReaderMode {
        val genre = manga.genre.orEmpty().lowercase()
        if (listOf("manhwa", "webtoon", "manhua", "long strip").any { it in genre }) return ReaderMode.WEBTOON
        val probe = runCatching { Images.pageFile(source, chapter.url, list.first()) }.getOrNull()
            ?: return ReaderMode.PAGED_RTL
        val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeFile(probe.absolutePath, opts)
        return if (opts.outWidth > 0 && opts.outHeight.toFloat() / opts.outWidth > 2.2f) ReaderMode.WEBTOON else ReaderMode.PAGED_RTL
    }

    private fun preload(from: Int) {
        preloadJob?.cancel()
        val list = pages
        val ahead = Yomu.prefs.preloadPages.get()
        preloadJob = scope.launch {
            for (i in from..minOf(from + ahead, list.lastIndex)) {
                runCatching { Images.pageFile(source, chapter.url, list[i]) }
            }
        }
    }

    private fun onPageChanged(page: Int) {
        if (pages.isEmpty()) return
        current = page
        preload(page)
        val finished = page >= pages.lastIndex
        Yomu.library.saveProgress(source.id, manga.url, chapter.url, chapter.name, page, finished)
    }

    private fun goTo(newIndex: Int) {
        if (newIndex !in chapters.indices) return
        index = newIndex
        initialPage = 0
        load()
    }

    @Composable
    override fun Content() {
        val nav = LocalNavigator.current
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            when {
                loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                error != null -> Column(
                    Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(error!!, color = Color.White)
                    Button(onClick = ::load) { Text("Erneut versuchen") }
                }
                else -> key(index, mode, reloadKey) {
                    when (mode) {
                        ReaderMode.WEBTOON, ReaderMode.AUTO -> Webtoon()
                        else -> Paged()
                    }
                }
            }

            if (!loading && error == null && Yomu.prefs.showPageNumber.get() && !menu) {
                Text(
                    "${current + 1} / ${pages.size}",
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 6.dp)
                        .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }

            if (menu) Menu(onBack = { nav.pop() })
        }
    }

    // ---------------------------------------------------------------- webtoon

    @Composable
    private fun Webtoon() {
        val list = pages
        val state = rememberLazyListState(initialFirstVisibleItemIndex = current.coerceIn(0, list.lastIndex))
        val scope = rememberCoroutineScope()
        var zoom by remember { mutableFloatStateOf(1f) }
        var panX by remember { mutableFloatStateOf(0f) }

        LaunchedEffect(state) {
            snapshotFlow { state.firstVisibleItemIndex }.distinctUntilChanged().collect { onPageChanged(it.coerceAtMost(list.lastIndex)) }
        }
        LaunchedEffect(Unit) {
            MainActivity.volumeKeys.collect { dir ->
                state.animateScrollBy(dir * state.layoutInfo.viewportSize.height * 0.8f)
            }
        }

        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, gestureZoom, _ ->
                        zoom = (zoom * gestureZoom).coerceIn(1f, 3f)
                        val maxPan = (size.width * (zoom - 1f)) / 2f
                        panX = (panX + pan.x).coerceIn(-maxPan, maxPan)
                        if (zoom == 1f) panX = 0f
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            zoom = if (zoom > 1f) 1f else 2f
                            panX = 0f
                        },
                        onTap = { offset ->
                            val h = size.height
                            when {
                                offset.y < h * 0.25f -> scope.launch { state.animateScrollBy(-h * 0.75f) }
                                offset.y > h * 0.75f -> scope.launch { state.animateScrollBy(h * 0.75f) }
                                else -> { menu = !menu }
                            }
                        },
                    )
                },
        ) {
            val widthPx = with(LocalDensity.current) { maxWidth.roundToPx() }
            LazyColumn(
                state = state,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = zoom
                        scaleY = zoom
                        translationX = panX
                    },
            ) {
                itemsIndexed(list, key = { i, _ -> "${chapter.url}#$i" }) { _, page ->
                    PageImage(page, widthPx, fit = false)
                }
                item { ChapterEnd() }
            }
        }
    }

    // ---------------------------------------------------------------- paged

    @Composable
    private fun Paged() {
        val list = pages
        val total = list.size + 1 // last slot = chapter end
        val pager = rememberPagerState(initialPage = current.coerceIn(0, list.lastIndex)) { total }
        val scope = rememberCoroutineScope()
        val rtl = mode == ReaderMode.PAGED_RTL
        val invert = Yomu.prefs.invertTapZones.get()

        LaunchedEffect(pager) {
            snapshotFlow { pager.currentPage }.distinctUntilChanged().collect { onPageChanged(it.coerceAtMost(list.lastIndex)) }
        }
        LaunchedEffect(Unit) {
            MainActivity.volumeKeys.collect { dir -> pager.animateScrollToPage((pager.currentPage + dir).coerceIn(0, total - 1)) }
        }

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val widthPx = with(LocalDensity.current) { maxWidth.roundToPx() }
            val pageContent: @Composable (Int) -> Unit = { i ->
                if (i < list.size) {
                    ZoomablePage(onTap = { offset, w ->
                        val third = w / 3f
                        val forwardOnRight = !rtl != invert
                        when {
                            offset.x < third -> scope.launch {
                                pager.animateScrollToPage((pager.currentPage + if (forwardOnRight) -1 else 1).coerceIn(0, total - 1))
                            }
                            offset.x > third * 2 -> scope.launch {
                                pager.animateScrollToPage((pager.currentPage + if (forwardOnRight) 1 else -1).coerceIn(0, total - 1))
                            }
                            else -> { menu = !menu }
                        }
                    }) { PageImage(list[i], widthPx, fit = true) }
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { ChapterEnd() }
                }
            }
            if (mode == ReaderMode.PAGED_VERTICAL) {
                VerticalPager(state = pager, beyondViewportPageCount = 1, modifier = Modifier.fillMaxSize()) { pageContent(it) }
            } else {
                HorizontalPager(
                    state = pager,
                    reverseLayout = rtl,
                    beyondViewportPageCount = 1,
                    modifier = Modifier.fillMaxSize(),
                ) { pageContent(it) }
            }
        }
    }

    @Composable
    private fun ZoomablePage(onTap: (Offset, Float) -> Unit, content: @Composable () -> Unit) {
        var zoom by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            if (zoom > 1f) {
                                zoom = 1f
                                offset = Offset.Zero
                            } else {
                                zoom = 2.5f
                            }
                        },
                        onTap = { onTap(it, size.width.toFloat()) },
                    )
                }
                .pointerInput(Unit) {
                    // Only claims gestures while zoomed (or pinching), so swiping pages still works.
                    awaitEachGestureZoom(
                        isZoomed = { zoom > 1f },
                        onZoom = { centroidPan, factor ->
                            zoom = (zoom * factor).coerceIn(1f, 5f)
                            val maxX = size.width * (zoom - 1f) / 2f
                            val maxY = size.height * (zoom - 1f) / 2f
                            offset = Offset(
                                (offset.x + centroidPan.x).coerceIn(-maxX, maxX),
                                (offset.y + centroidPan.y).coerceIn(-maxY, maxY),
                            )
                            if (zoom <= 1f) offset = Offset.Zero
                        },
                    )
                }
                .graphicsLayer {
                    scaleX = zoom
                    scaleY = zoom
                    translationX = offset.x
                    translationY = offset.y
                },
            contentAlignment = Alignment.Center,
        ) { content() }
    }

    // ---------------------------------------------------------------- shared

    @Composable
    private fun PageImage(page: Page, widthPx: Int, fit: Boolean) {
        var bitmap by remember(page) { mutableStateOf<Bitmap?>(null) }
        var failed by remember(page) { mutableStateOf<String?>(null) }
        var attempt by remember(page) { mutableIntStateOf(0) }

        LaunchedEffect(page, attempt) {
            failed = null
            try {
                val file: File = Images.pageFile(source, chapter.url, page)
                bitmap = Images.pageBitmap(file, widthPx) ?: error("Bild kann nicht gelesen werden")
            } catch (e: Throwable) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                failed = e.message ?: e.javaClass.simpleName
            }
        }

        val bmp = bitmap
        when {
            bmp != null -> Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Seite ${page.number}",
                contentScale = if (fit) ContentScale.Fit else ContentScale.FillWidth,
                modifier = if (fit) Modifier.fillMaxSize() else Modifier.fillMaxWidth().aspectRatio(bmp.width.toFloat() / bmp.height),
            )
            failed != null -> Column(
                Modifier.fillMaxWidth().height(320.dp).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
            ) {
                Text("Seite ${page.number}: $failed", color = Color.White, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Button(onClick = { attempt++ }) { Text("Nochmal") }
            }
            else -> Box(
                if (fit) Modifier.fillMaxSize() else Modifier.fillMaxWidth().height(480.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Text("${page.number}", color = Color.Gray, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
    }

    @Composable
    private fun ChapterEnd() {
        val hasNext = index < chapters.lastIndex
        Column(
            Modifier.fillMaxWidth().padding(vertical = 64.dp, horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Ende von: ${chapter.name}", color = Color.White, style = MaterialTheme.typography.titleMedium)
            if (hasNext) {
                Text("Weiter: ${chapters[index + 1].name}", color = Color.LightGray)
                Button(onClick = { goTo(index + 1) }) { Text("Nächstes Kapitel") }
            } else {
                Text("Das war das neueste Kapitel 🎉", color = Color.LightGray)
            }
        }
    }

    @Composable
    private fun Menu(onBack: () -> Unit) {
        Box(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.85f))
                    .statusBarsPadding()
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück", tint = Color.White) }
                Column(Modifier.weight(1f)) {
                    Text(manga.title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(chapter.name, color = Color.LightGray, maxLines = 1, style = MaterialTheme.typography.bodySmall)
                }
            }
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.85f))
                    .navigationBarsPadding()
                    .padding(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { goTo(index - 1) }, enabled = index > 0) { Text("◀ Vorheriges") }
                    Text(
                        "${current + 1} / ${pages.size}",
                        color = Color.White,
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    TextButton(onClick = { goTo(index + 1) }, enabled = index < chapters.lastIndex) { Text("Nächstes ▶") }
                }
                if (pages.size > 1) {
                    var slider by remember(current) { mutableFloatStateOf(current.toFloat()) }
                    Slider(
                        value = slider,
                        onValueChange = { slider = it },
                        onValueChangeFinished = {
                            initialPage = slider.toInt()
                            current = initialPage
                            // Re-key the reader at the chosen page.
                            reloadKey++
                        },
                        valueRange = 0f..pages.lastIndex.toFloat(),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        ReaderMode.WEBTOON to "Webtoon",
                        ReaderMode.PAGED_RTL to "Manga ←",
                        ReaderMode.PAGED_LTR to "Comic →",
                        ReaderMode.PAGED_VERTICAL to "Vertikal",
                    ).forEach { (m, label) ->
                        FilterChip(selected = mode == m, onClick = { mode = m }, label = { Text(label) })
                    }
                }
            }
        }
    }
}

/** Pinch-zoom/pan that only consumes events while pinching or already zoomed in. */
private suspend fun PointerInputScope.awaitEachGestureZoom(
    isZoomed: () -> Boolean,
    onZoom: (pan: Offset, factor: Float) -> Unit,
) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        do {
            val event = awaitPointerEvent()
            val fingers = event.changes.count { it.pressed }
            if (fingers >= 2 || isZoomed()) {
                onZoom(event.calculatePan(), event.calculateZoom())
                event.changes.forEach { if (it.positionChanged()) it.consume() }
            }
        } while (event.changes.any { it.pressed })
    }
}

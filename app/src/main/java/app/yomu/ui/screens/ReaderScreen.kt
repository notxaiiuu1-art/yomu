package app.yomu.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
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
import app.yomu.ai.Gemini
import app.yomu.ai.PageScript
import app.yomu.ai.Speaker
import app.yomu.ai.SpokenLine
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
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
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

    // AI read-aloud
    private var aiOn by mutableStateOf(false)
    private var aiStatus by mutableStateOf<String?>(null)
    private var caption by mutableStateOf<SpokenLine?>(null)
    private var aiJob: Job? = null
    private val scripts = HashMap<String, Deferred<PageScript>>()

    /** Where read-aloud wants the reader to be: a page, and optionally a spot on it (0 = top, 1 = bottom). */
    private data class ScrollCommand(val chapterIndex: Int, val pageIndex: Int, val y: Float = -1f)

    private val pageCommands = MutableSharedFlow<ScrollCommand>(extraBufferCapacity = 8)

    private val isWebtoon get() = mode == ReaderMode.WEBTOON || mode == ReaderMode.AUTO

    private val chapter get() = chapters[index]

    // Webtoon mode reads chapters as one endless strip: the next chapter is appended below.
    private class StripPage(val chapterIndex: Int, val pageIndex: Int, val page: Page)
    private val strip = mutableStateListOf<StripPage>()
    private val chapterPages = HashMap<Int, List<Page>>()
    private var appending by mutableStateOf(false)
    private var appendError by mutableStateOf<String?>(null)

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
                chapterPages.clear()
                chapterPages[index] = list
                strip.clear()
                strip.addAll(list.mapIndexed { i, page -> StripPage(index, i, page) })
                appendError = null
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

    /** Loads the chapter after the last one in the strip and appends its pages. */
    private fun appendNext() {
        val last = strip.lastOrNull()?.chapterIndex ?: return
        val next = last + 1
        if (appending || next > chapters.lastIndex || chapterPages.containsKey(next)) return
        appending = true
        appendError = null
        scope.launch {
            try {
                val nextChapter = chapters[next]
                val list = withContext(Dispatchers.IO) { source.getPageList(nextChapter) }
                if (list.isNotEmpty()) {
                    chapterPages[next] = list
                    strip.addAll(list.mapIndexed { i, page -> StripPage(next, i, page) })
                    launch {
                        list.take(3).forEach { runCatching { Images.pageFile(source, nextChapter.url, it) } }
                    }
                }
            } catch (e: Throwable) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                appendError = e.message ?: e.javaClass.simpleName
            } finally {
                appending = false
            }
        }
    }

    /** Called with the first visible strip position; switches chapter when the reader crosses into the next one. */
    private fun onStripVisible(position: Int) {
        if (strip.isEmpty()) return
        val item = strip[position.coerceIn(0, strip.lastIndex)]
        if (item.chapterIndex != index) {
            // Crossing a chapter border: the previous chapter counts as read.
            if (item.chapterIndex > index) {
                Yomu.library.saveProgress(source.id, manga.url, chapter.url, chapter.name, pages.lastIndex, true)
            }
            index = item.chapterIndex
            pages = chapterPages[index].orEmpty()
        }
        onPageChanged(item.pageIndex)
    }

    private fun onPageChanged(page: Int) {
        if (pages.isEmpty()) return
        current = page
        preload(page)
        val finished = page >= pages.lastIndex
        Yomu.library.saveProgress(source.id, manga.url, chapter.url, chapter.name, page, finished)
    }

    override fun onDispose() {
        aiJob?.cancel()
        Yomu.readAloud.stop()
        super.onDispose()
    }

    // ---------------------------------------------------------------- AI read-aloud

    private fun scriptKey(i: Int) = scriptKeyFor(chapter, i)

    private fun scriptKeyFor(ch: SChapter, i: Int) = "${ch.url}#$i"

    /**
     * Script for page [i]. Missing pages are requested in batches of up to six, so a chapter
     * costs only a few Gemini requests (the free tier allows very few per day).
     */
    private fun scriptFor(i: Int): Deferred<PageScript> {
        scripts[scriptKey(i)]?.let { return it }
        val ch = chapter
        val list = pages
        val rtl = mode == ReaderMode.PAGED_RTL
        val batch = (i..minOf(i + 5, list.lastIndex)).filter { scripts[scriptKeyFor(ch, it)] == null }
        val request = scope.async(Dispatchers.IO) {
            val files = batch.map { Images.pageFile(source, ch.url, list[it]) }
            Gemini.readPages(files, rtl)
        }
        batch.forEachIndexed { n, page ->
            scripts[scriptKeyFor(ch, page)] = scope.async { request.await()[n] }
        }
        return scripts.getValue(scriptKey(i))
    }

    private fun toggleAi() {
        if (aiOn) {
            stopAi()
            return
        }
        if (Yomu.prefs.geminiKey.get().isBlank()) {
            aiStatus = "Kein Gemini-Key. Trag ihn unter Einstellungen → KI-Vorlesen ein."
            return
        }
        aiOn = true
        menu = false
        aiStatus = null
        aiJob = scope.launch { runAi() }
    }

    private fun stopAi(message: String? = null) {
        aiOn = false
        aiJob?.cancel()
        aiJob = null
        Yomu.readAloud.stop()
        caption = null
        aiStatus = message
    }

    private suspend fun runAi() {
        val fallbackLang = if (Yomu.prefs.aiTranslateGerman.get()) "de" else "en"
        while (aiOn) {
            if (loading) snapshotFlow { loading }.first { !it }
            if (error != null || pages.isEmpty()) {
                stopAi("Kapitel konnte nicht geladen werden.")
                return
            }
            val i = current.coerceIn(0, pages.lastIndex)
            aiStatus = "KI liest Seite ${i + 1} …"
            val script = try {
                scriptFor(i).await()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                scripts.clear()
                stopAi(e.message ?: "KI-Fehler")
                return
            }
            // Analyse the next page while this one is being read out.
            if (i < pages.lastIndex) scriptFor(i + 1)
            aiStatus = null

            for (line in script.lines) {
                if (!aiOn || current != i) break
                caption = line
                // Webtoon: glide to the bubble that is being read.
                if (isWebtoon && line.y >= 0f) pageCommands.emit(ScrollCommand(index, i, line.y))
                Yomu.readAloud.speak(line, script.language.ifBlank { fallbackLang })
            }
            caption = null
            if (!aiOn) return
            if (current != i) continue // the user turned the page: read that one instead

            if (i >= pages.lastIndex) {
                if (index < chapters.lastIndex && isWebtoon) {
                    // Keep scrolling into the next chapter instead of reloading the reader.
                    val next = index + 1
                    appendNext()
                    withTimeoutOrNull(30_000) {
                        snapshotFlow { strip.any { it.chapterIndex == next } || appendError != null }.first { it }
                    }
                    if (strip.any { it.chapterIndex == next }) {
                        pageCommands.emit(ScrollCommand(next, 0))
                        withTimeoutOrNull(4_000) { snapshotFlow { index }.first { it == next } }
                    }
                    if (index != next) goTo(next)
                } else if (index < chapters.lastIndex) {
                    goTo(index + 1)
                } else {
                    stopAi("Fertig – das war das neueste Kapitel.")
                    return
                }
            } else {
                pageCommands.emit(ScrollCommand(index, i + 1))
                withTimeoutOrNull(4_000) { snapshotFlow { current }.first { it != i } }
                if (current == i) {
                    stopAi("Konnte nicht weiterblättern.")
                    return
                }
            }
        }
    }

    @Composable
    private fun AiOverlay(modifier: Modifier = Modifier) {
        val status = aiStatus
        val line = caption
        val showCaptions by Yomu.prefs.showCaptions.flow.collectAsState()
        Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (aiOn || status != null) {
                Row(
                    Modifier
                        .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(20.dp))
                        .clickable { if (aiOn) stopAi() else aiStatus = null }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (aiOn && status != null) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text(
                        status ?: "🔊 Liest vor · tippen zum Stoppen",
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
            if (line != null && showCaptions) {
                val who = when (line.speaker) {
                    Speaker.FEMALE -> "👩"
                    Speaker.MALE -> "👨"
                    Speaker.CHILD -> "🧒"
                    Speaker.NARRATOR -> "📖"
                }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(14.dp))
                        .padding(14.dp),
                ) {
                    Text(
                        "$who ${line.name}".trim(),
                        color = Color.LightGray,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(line.text, color = Color.White, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
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
                // Webtoon keeps one strip across chapters, so it must not be rebuilt on chapter change.
                else -> key(if (mode == ReaderMode.PAGED_RTL || mode == ReaderMode.PAGED_LTR || mode == ReaderMode.PAGED_VERTICAL) index else -1, mode, reloadKey) {
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

            if (aiOn || aiStatus != null || caption != null) {
                AiOverlay(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(start = 12.dp, end = 12.dp, bottom = 36.dp),
                )
            }

            if (menu) Menu(onBack = { nav.pop() })
        }
    }

    // ---------------------------------------------------------------- webtoon

    @Composable
    private fun Webtoon() {
        val state = rememberLazyListState(initialFirstVisibleItemIndex = current.coerceIn(0, (strip.size - 1).coerceAtLeast(0)))
        val scope = rememberCoroutineScope()
        var zoom by remember { mutableFloatStateOf(1f) }
        var panX by remember { mutableFloatStateOf(0f) }

        LaunchedEffect(state) {
            snapshotFlow { state.firstVisibleItemIndex }.distinctUntilChanged().collect { onStripVisible(it) }
        }
        // Start loading the next chapter a few pages before the end, so it is simply there.
        LaunchedEffect(state) {
            snapshotFlow {
                val info = state.layoutInfo
                (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - 4
            }.distinctUntilChanged().collect { nearEnd -> if (nearEnd) appendNext() }
        }
        LaunchedEffect(Unit) {
            pageCommands.collect { cmd ->
                val position = strip.indexOfFirst { it.chapterIndex == cmd.chapterIndex && it.pageIndex == cmd.pageIndex }
                if (position < 0) return@collect
                if (cmd.y < 0f) {
                    state.animateScrollToItem(position)
                    return@collect
                }
                // Need the page's real height: bring it on screen once if it isn't yet.
                var info = state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == position }
                if (info == null) {
                    state.scrollToItem(position)
                    withFrameNanos { }
                    info = state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == position }
                }
                val height = info?.size ?: return@collect
                val viewport = state.layoutInfo.viewportSize.height
                // Put the bubble about a third from the top of the screen.
                val offset = (cmd.y * height - viewport * 0.33f).toInt().coerceIn(0, (height - 1).coerceAtLeast(0))
                state.animateScrollToItem(position, offset)
            }
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
                    // Two-finger pinch only, so one-finger scrolling stays with the list.
                    awaitEachGestureZoom(isZoomed = { false }) { pan, factor ->
                        zoom = (zoom * factor).coerceIn(1f, 3f)
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
                items(strip.size, key = { pos -> strip[pos].let { "${it.chapterIndex}#${it.pageIndex}" } }) { pos ->
                    val item = strip[pos]
                    val itemChapter = chapters[item.chapterIndex]
                    Column {
                        if (item.pageIndex == 0 && pos > 0) {
                            Text(
                                "— ${itemChapter.name} —",
                                color = Color.Gray,
                                style = MaterialTheme.typography.labelLarge,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                            )
                        }
                        PageImage(item.page, widthPx, fit = false, chapterUrl = itemChapter.url)
                    }
                }
                item {
                    val lastInStrip = strip.lastOrNull()?.chapterIndex ?: index
                    when {
                        appending -> Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                        appendError != null -> Column(
                            Modifier.fillMaxWidth().padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text("Nächstes Kapitel: ${appendError}", color = Color.White)
                            Button(onClick = ::appendNext) { Text("Nochmal") }
                        }
                        lastInStrip >= chapters.lastIndex -> ChapterEnd()
                        else -> Box(Modifier.fillMaxWidth().height(200.dp))
                    }
                }
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
            pageCommands.collect { cmd ->
                if (cmd.chapterIndex == index) pager.animateScrollToPage(cmd.pageIndex.coerceIn(0, total - 1))
            }
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
    private fun PageImage(page: Page, widthPx: Int, fit: Boolean, chapterUrl: String = chapter.url) {
        var bitmap by remember(page) { mutableStateOf<Bitmap?>(null) }
        var failed by remember(page) { mutableStateOf<String?>(null) }
        var attempt by remember(page) { mutableIntStateOf(0) }

        LaunchedEffect(page, attempt, chapterUrl) {
            failed = null
            try {
                val file: File = Images.pageFile(source, chapterUrl, page)
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
                Button(onClick = ::toggleAi, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    Text(if (aiOn) "⏹ Vorlesen stoppen" else "🔊 KI-Vorlesen starten")
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

@file:Suppress("unused")

package eu.kanade.tachiyomi.source.online

import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/** HTML-scraping helper base used by older (lib 1.4) extensions. */
abstract class ParsedHttpSource : HttpSource() {

    private fun parseList(
        response: Response,
        selector: String,
        nextSelector: String?,
        fromElement: (Element) -> SManga,
    ): MangasPage {
        val doc = response.asJsoup()
        val mangas = doc.select(selector).map(fromElement)
        val hasNext = nextSelector?.let { doc.selectFirst(it) } != null
        return MangasPage(mangas, hasNext)
    }

    override fun popularMangaParse(response: Response): MangasPage =
        parseList(response, popularMangaSelector(), popularMangaNextPageSelector(), ::popularMangaFromElement)

    protected abstract fun popularMangaSelector(): String
    protected abstract fun popularMangaFromElement(element: Element): SManga
    protected abstract fun popularMangaNextPageSelector(): String?

    override fun searchMangaParse(response: Response): MangasPage =
        parseList(response, searchMangaSelector(), searchMangaNextPageSelector(), ::searchMangaFromElement)

    protected abstract fun searchMangaSelector(): String
    protected abstract fun searchMangaFromElement(element: Element): SManga
    protected abstract fun searchMangaNextPageSelector(): String?

    override fun latestUpdatesParse(response: Response): MangasPage =
        parseList(response, latestUpdatesSelector(), latestUpdatesNextPageSelector(), ::latestUpdatesFromElement)

    protected abstract fun latestUpdatesSelector(): String
    protected abstract fun latestUpdatesFromElement(element: Element): SManga
    protected abstract fun latestUpdatesNextPageSelector(): String?

    override fun mangaDetailsParse(response: Response): SManga = mangaDetailsParse(response.asJsoup())

    protected abstract fun mangaDetailsParse(document: Document): SManga

    override fun chapterListParse(response: Response): List<SChapter> =
        response.asJsoup().select(chapterListSelector()).map { chapterFromElement(it) }

    protected abstract fun chapterListSelector(): String
    protected abstract fun chapterFromElement(element: Element): SChapter

    override fun pageListParse(response: Response): List<Page> = pageListParse(response.asJsoup())

    protected abstract fun pageListParse(document: Document): List<Page>

    override fun imageUrlParse(response: Response): String = imageUrlParse(response.asJsoup())

    protected abstract fun imageUrlParse(document: Document): String
}

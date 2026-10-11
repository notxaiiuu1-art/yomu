@file:Suppress("unused", "MemberVisibilityCanBePrivate", "DEPRECATION")

package eu.kanade.tachiyomi.source.online

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.awaitSingle
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.net.URI
import java.net.URISyntaxException
import java.security.MessageDigest

/**
 * Base class for sources that talk HTTP. Handles both the request/parse style of lib-1.4
 * extensions and the suspend overrides of lib-1.6 extensions.
 */
abstract class HttpSource : CatalogueSource {

    protected val network: NetworkHelper by injectLazy()

    abstract val baseUrl: String

    open fun getHomeUrl(): String = baseUrl

    open val versionId: Int = 1

    override val id: Long by lazy { generateId(name, lang, versionId) }

    val headers: Headers by lazy { headersBuilder().build() }

    open val client: OkHttpClient
        get() = network.client

    /** Image downloads bypass the small HTTP cache so pages don't evict API responses. */
    private val imageClient: OkHttpClient by lazy { client.newBuilder().cache(null).build() }

    /**
     * Source ids are the first 8 bytes of MD5("name/lang/version"). The repo index lists the
     * same ids, which is how installed sources and the catalogue line up.
     */
    protected fun generateId(name: String, lang: String, versionId: Int): Long {
        val digest = MessageDigest.getInstance("MD5").digest("${name.lowercase()}/$lang/$versionId".toByteArray())
        var result = 0L
        for (i in 0 until 8) result = result or ((digest[i].toLong() and 0xff) shl (8 * (7 - i)))
        return result and Long.MAX_VALUE
    }

    protected open fun headersBuilder(): Headers.Builder =
        Headers.Builder().add("User-Agent", network.defaultUserAgentProvider())

    override fun toString(): String = "$name (${lang.uppercase()})"

    // ---- popular ----

    override fun fetchPopularManga(page: Int): Observable<MangasPage> =
        client.newCall(popularMangaRequest(page)).asObservableSuccess().map { popularMangaParse(it) }

    protected open fun popularMangaRequest(page: Int): Request = throw UnsupportedOperationException()

    protected open fun popularMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()

    // ---- search ----

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> =
        Observable.defer<Response> { client.newCall(searchMangaRequest(page, query, filters)).asObservableSuccess() }
            .map { searchMangaParse(it) }

    protected open fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request =
        throw UnsupportedOperationException()

    protected open fun searchMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()

    // ---- latest ----

    override fun fetchLatestUpdates(page: Int): Observable<MangasPage> =
        client.newCall(latestUpdatesRequest(page)).asObservableSuccess().map { latestUpdatesParse(it) }

    protected open fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()

    protected open fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()

    // ---- details ----

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> =
        client.newCall(mangaDetailsRequest(manga)).asObservableSuccess()
            .map { mangaDetailsParse(it).apply { initialized = true } }

    open fun mangaDetailsRequest(manga: SManga): Request = GET(baseUrl + manga.url, headers)

    protected open fun mangaDetailsParse(response: Response): SManga = throw UnsupportedOperationException()

    // ---- chapters ----

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> =
        client.newCall(chapterListRequest(manga)).asObservableSuccess().map { chapterListParse(it) }

    protected open fun chapterListRequest(manga: SManga): Request = GET(baseUrl + manga.url, headers)

    protected open fun chapterListParse(response: Response): List<SChapter> = throw UnsupportedOperationException()

    // ---- pages ----

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> =
        client.newCall(pageListRequest(chapter)).asObservableSuccess().map { pageListParse(it) }

    protected open fun pageListRequest(chapter: SChapter): Request = GET(baseUrl + chapter.url, headers)

    protected open fun pageListParse(response: Response): List<Page> = throw UnsupportedOperationException()

    open fun fetchImageUrl(page: Page): Observable<String> =
        client.newCall(imageUrlRequest(page)).asObservableSuccess().map { imageUrlParse(it) }

    open suspend fun getImageUrl(page: Page): String = fetchImageUrl(page).awaitSingle()

    protected open fun imageUrlRequest(page: Page): Request = GET(page.url, headers)

    protected open fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    protected open fun imageRequest(page: Page): Request = GET(page.imageUrl!!, headers)

    /** Fetches the image bytes of [page]. The caller closes the response. */
    suspend fun getImage(page: Page): Response = imageClient.newCall(imageRequest(page)).awaitSuccess()

    // ---- urls ----

    fun SChapter.setUrlWithoutDomain(url: String) {
        this.url = stripDomain(url)
    }

    fun SManga.setUrlWithoutDomain(url: String) {
        this.url = stripDomain(url)
    }

    private fun stripDomain(orig: String): String = try {
        val uri = URI(orig.replace(" ", "%20"))
        buildString {
            append(uri.path)
            uri.query?.let { append('?').append(it) }
            uri.fragment?.let { append('#').append(it) }
        }
    } catch (_: URISyntaxException) {
        orig
    }

    open fun getMangaUrl(manga: SManga): String = mangaDetailsRequest(manga).url.toString()

    open fun getChapterUrl(chapter: SChapter): String = pageListRequest(chapter).url.toString()

    /** Lets lib-1.4 sources adjust freshly fetched chapters. */
    open fun prepareNewChapter(chapter: SChapter, manga: SManga) {}
}

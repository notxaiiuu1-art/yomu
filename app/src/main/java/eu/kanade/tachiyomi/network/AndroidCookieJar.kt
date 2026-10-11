package eu.kanade.tachiyomi.network

import android.webkit.CookieManager
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/** Shares cookies between OkHttp and WebView, so a challenge solved in the browser sticks. */
class AndroidCookieJar : CookieJar {

    val manager: CookieManager = CookieManager.getInstance()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val urlString = url.toString()
        cookies.forEach { manager.setCookie(urlString, it.toString()) }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> = get(url)

    fun get(url: HttpUrl): List<Cookie> {
        val raw = manager.getCookie(url.toString()) ?: return emptyList()
        return raw.split(";").mapNotNull { Cookie.parse(url, it.trim()) }
    }

    fun remove(url: HttpUrl, cookieNames: List<String>? = null): Int {
        val urlString = url.toString()
        val raw = manager.getCookie(urlString) ?: return 0
        val names = raw.split(";").map { it.substringBefore("=").trim() }
            .filter { cookieNames == null || it in cookieNames }
        names.forEach { manager.setCookie(urlString, "$it=;Max-Age=-1") }
        return names.size
    }

    fun removeAll() = manager.removeAllCookies {}
}

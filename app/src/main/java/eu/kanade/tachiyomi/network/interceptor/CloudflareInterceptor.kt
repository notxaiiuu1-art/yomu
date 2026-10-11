package eu.kanade.tachiyomi.network.interceptor

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import android.webkit.WebViewClient
import eu.kanade.tachiyomi.network.AndroidCookieJar
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Solves Cloudflare's JavaScript challenge in an invisible WebView, then retries the request
 * with the clearance cookie. Interactive captchas can't be solved this way; for those the
 * error tells the user to open the site in Yomu's browser once.
 */
class CloudflareInterceptor(
    private val context: Context,
    private val cookieJar: AndroidCookieJar,
    private val userAgentProvider: () -> String,
) : Interceptor {

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        if (!isChallenge(response)) return response

        response.close()
        val oldClearance = clearance(request)
        // Old clearance cookies make the challenge loop, so drop them first.
        cookieJar.remove(request.url, listOf(CLEARANCE, "__cf_bm"))

        if (!solve(request, oldClearance)) {
            throw IOException(
                "Cloudflare blockiert ${request.url.host}. Öffne die Quelle einmal im Browser (Weltkugel-Symbol) und versuch es erneut.",
            )
        }
        return chain.proceed(request)
    }

    private fun isChallenge(response: Response): Boolean {
        if (response.code != 403 && response.code != 503 && response.code != 429) return false
        if (response.header("cf-mitigated") == "challenge") return true
        return response.header("Server").orEmpty().startsWith("cloudflare", ignoreCase = true) &&
            response.peekBody(64 * 1024).string().let {
                "challenge-platform" in it || "cf-browser-verification" in it || "Just a moment" in it
            }
    }

    private fun clearance(request: Request): String? =
        cookieJar.get(request.url).firstOrNull { it.name == CLEARANCE }?.value

    @SuppressLint("SetJavaScriptEnabled")
    private fun solve(request: Request, oldClearance: String?): Boolean {
        val latch = CountDownLatch(1)
        var webView: WebView? = null
        var solved = false
        val headers = request.headers.toMultimap().mapValues { it.value.joinToString(",") }
            .filterKeys { !it.equals("Cookie", true) }

        val check = object : Runnable {
            override fun run() {
                val now = clearance(request)
                if (now != null && now != oldClearance) {
                    solved = true
                    latch.countDown()
                } else if (latch.count > 0) {
                    mainHandler.postDelayed(this, 500)
                }
            }
        }

        mainHandler.post {
            val view = WebView(context)
            webView = view
            with(view.settings) {
                javaScriptEnabled = true
                domStorageEnabled = true
                userAgentString = request.header("User-Agent") ?: userAgentProvider()
            }
            view.webViewClient = WebViewClient()
            cookieJar.manager.setAcceptThirdPartyCookies(view, true)
            view.loadUrl(request.url.toString(), headers)
            mainHandler.postDelayed(check, 1000)
        }

        latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        mainHandler.post {
            mainHandler.removeCallbacks(check)
            webView?.run {
                stopLoading()
                destroy()
            }
        }
        return solved
    }

    private companion object {
        const val CLEARANCE = "cf_clearance"
        const val TIMEOUT_SECONDS = 30L
    }
}

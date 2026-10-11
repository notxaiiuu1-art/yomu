package eu.kanade.tachiyomi.network

import android.content.Context
import android.webkit.WebSettings
import eu.kanade.tachiyomi.network.interceptor.CloudflareInterceptor
import eu.kanade.tachiyomi.network.interceptor.UncaughtExceptionInterceptor
import eu.kanade.tachiyomi.network.interceptor.UserAgentInterceptor
import okhttp3.Cache
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.io.File
import java.net.InetAddress
import java.util.concurrent.TimeUnit

/**
 * The shared HTTP client handed to every extension. Extensions read [client] (and the old
 * [cloudflareClient] alias) and call [defaultUserAgentProvider] for their headers.
 */
class NetworkHelper(private val context: Context) {

    private val prefs = context.getSharedPreferences("network", Context.MODE_PRIVATE)

    val cookieJar = AndroidCookieJar()

    private val fallbackUserAgent by lazy {
        runCatching { WebSettings.getDefaultUserAgent(context) }
            .getOrDefault(
                "Mozilla/5.0 (Linux; Android 16; Pixel 8a) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Mobile Safari/537.36",
            )
            // Sites treat the WebView marker as a bot; present as regular Chrome.
            .replace("; wv)", ")")
            .replace(Regex("Version/\\d+\\.\\d+ "), "")
    }

    val client: OkHttpClient by lazy {
        val builder = OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(2, TimeUnit.MINUTES)
            .cache(Cache(File(context.cacheDir, "network_cache"), 8L * 1024 * 1024))
            // Keiyoushi extensions require these three, in this order, and add their own
            // compression handling (so no Brotli interceptor here).
            .addInterceptor(UncaughtExceptionInterceptor())
            .addInterceptor(UserAgentInterceptor(::defaultUserAgentProvider))
            .addInterceptor(CloudflareInterceptor(context, cookieJar, ::defaultUserAgentProvider))

        if (prefs.getBoolean(PREF_DOH, false)) {
            val bootstrap = OkHttpClient.Builder().build()
            builder.dns(
                DnsOverHttps.Builder()
                    .client(bootstrap)
                    .url("https://cloudflare-dns.com/dns-query".toHttpUrl())
                    .bootstrapDnsHosts(
                        listOf("1.1.1.1", "1.0.0.1", "2606:4700:4700::1111").map { InetAddress.getByName(it) },
                    )
                    .build(),
            )
        }
        builder.build()
    }

    @Deprecated("The regular client handles Cloudflare by default")
    @Suppress("unused")
    val cloudflareClient: OkHttpClient
        get() = client

    fun defaultUserAgentProvider(): String =
        prefs.getString(PREF_USER_AGENT, null)?.trim()?.takeIf { it.isNotEmpty() } ?: fallbackUserAgent

    companion object {
        const val PREF_DOH = "doh_cloudflare"
        const val PREF_USER_AGENT = "user_agent"
    }
}

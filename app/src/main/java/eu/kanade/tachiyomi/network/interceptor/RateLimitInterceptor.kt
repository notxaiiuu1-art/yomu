@file:Suppress("unused")

package eu.kanade.tachiyomi.network.interceptor

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException
import java.util.ArrayDeque
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toDuration
import kotlin.time.toDurationUnit

fun OkHttpClient.Builder.rateLimit(permits: Int, period: Long = 1, unit: TimeUnit = TimeUnit.SECONDS) =
    addInterceptor(RateLimitInterceptor(null, permits, period.toDuration(unit.toDurationUnit())))

fun OkHttpClient.Builder.rateLimit(permits: Int, period: Duration = 1.seconds) =
    addInterceptor(RateLimitInterceptor(null, permits, period))

fun OkHttpClient.Builder.rateLimitHost(
    httpUrl: HttpUrl,
    permits: Int,
    period: Long = 1,
    unit: TimeUnit = TimeUnit.SECONDS,
) = addInterceptor(RateLimitInterceptor(httpUrl.host, permits, period.toDuration(unit.toDurationUnit())))

fun OkHttpClient.Builder.rateLimitHost(httpUrl: HttpUrl, permits: Int, period: Duration = 1.seconds) =
    addInterceptor(RateLimitInterceptor(httpUrl.host, permits, period))

fun OkHttpClient.Builder.rateLimitHost(url: String, permits: Int, period: Duration = 1.seconds) =
    addInterceptor(RateLimitInterceptor(url.toHttpUrlOrNull()?.host, permits, period))

/**
 * Sliding-window limiter: at most [permits] requests per [period], optionally only for [host].
 * Waiting requests can be cancelled at any time.
 */
internal class RateLimitInterceptor(
    private val host: String?,
    private val permits: Int,
    period: Duration,
) : Interceptor {

    private val windowMs = period.inWholeMilliseconds
    private val sent = ArrayDeque<Long>(permits)
    private val lock = Object()

    init {
        require(permits > 0) { "permits must be positive" }
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val call = chain.call()
        if (call.isCanceled()) throw IOException("Canceled")
        val request = chain.request()
        if (host != null && request.url.host != host) return chain.proceed(request)

        synchronized(lock) {
            while (true) {
                val now = System.currentTimeMillis()
                while (sent.isNotEmpty() && now - sent.first() >= windowMs) sent.removeFirst()
                if (sent.size < permits) {
                    sent.addLast(now)
                    break
                }
                val wait = (windowMs - (now - sent.first())).coerceIn(1, 500)
                lock.wait(wait)
                if (call.isCanceled()) throw IOException("Canceled")
            }
        }
        return chain.proceed(request)
    }
}

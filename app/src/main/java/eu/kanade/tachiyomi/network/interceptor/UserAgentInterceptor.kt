package eu.kanade.tachiyomi.network.interceptor

import okhttp3.Interceptor
import okhttp3.Response

/** Fills in the app's browser User-Agent when a request doesn't set one. */
class UserAgentInterceptor(private val defaultUserAgentProvider: () -> String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!request.header("User-Agent").isNullOrBlank()) return chain.proceed(request)
        return chain.proceed(
            request.newBuilder().header("User-Agent", defaultUserAgentProvider()).build(),
        )
    }
}

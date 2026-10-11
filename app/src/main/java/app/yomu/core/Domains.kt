package app.yomu.core

import app.yomu.Yomu
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Per-source address override. When a site moves to a new domain, the user enters the new
 * address and every request to the old host is redirected there – no extension update needed.
 */
object Domains {

    private fun overrides(): Map<String, HttpUrl> = Yomu.prefs.domainOverrides.get().mapNotNull { entry ->
        val old = entry.substringBefore('|')
        val new = entry.substringAfter('|', "").toHttpUrlOrNull() ?: return@mapNotNull null
        old to new
    }.toMap()

    fun hostOf(url: String): String? = url.toHttpUrlOrNull()?.host

    fun overrideFor(originalBaseUrl: String): String? {
        val host = hostOf(originalBaseUrl) ?: return null
        return overrides()[host]?.toString()?.trimEnd('/')
    }

    fun set(originalBaseUrl: String, newAddress: String?) {
        val host = hostOf(originalBaseUrl) ?: return
        val kept = Yomu.prefs.domainOverrides.get().filterNot { it.substringBefore('|') == host }.toSet()
        val normalized = newAddress?.trim()?.takeIf { it.isNotEmpty() }
            ?.let { if (it.startsWith("http")) it else "https://$it" }
            ?.toHttpUrlOrNull()
            ?.let { "${it.scheme}://${it.host}" + if (it.port != HttpUrl.defaultPort(it.scheme)) ":${it.port}" else "" }
        Yomu.prefs.domainOverrides.set(if (normalized != null) kept + "$host|$normalized" else kept)
    }

    /** Rewrites a URL if its host has an override (also handles a leading "www."). */
    fun rewrite(url: HttpUrl): HttpUrl {
        val map = overrides()
        if (map.isEmpty()) return url
        val target = map[url.host] ?: map[url.host.removePrefix("www.")] ?: return url
        return url.newBuilder().scheme(target.scheme).host(target.host).port(target.port).build()
    }

    fun rewrite(url: String): String = url.toHttpUrlOrNull()?.let { rewrite(it).toString() } ?: url
}

class DomainOverrideInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val rewritten = Domains.rewrite(request.url)
        if (rewritten == request.url) return chain.proceed(request)
        val builder = request.newBuilder().url(rewritten)
        // Keep referer/origin pointing at the site we actually talk to.
        request.header("Referer")?.let { builder.header("Referer", Domains.rewrite(it)) }
        request.header("Origin")?.let { builder.header("Origin", Domains.rewrite(it).trimEnd('/')) }
        return chain.proceed(builder.build())
    }
}

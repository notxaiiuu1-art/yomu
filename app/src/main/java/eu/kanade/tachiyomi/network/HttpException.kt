package eu.kanade.tachiyomi.network

/** Thrown for non-2xx responses. Extensions catch it and read [code]. */
class HttpException(val code: Int) : IllegalStateException("HTTP error $code")

package eu.kanade.tachiyomi.network.interceptor

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * Turns unexpected crashes inside a source's request code into ordinary network errors, so a
 * broken extension shows an error message instead of taking the app down. Keiyoushi extensions
 * also verify that the shared client contains this interceptor (by class name).
 */
class UncaughtExceptionInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response = try {
        chain.proceed(chain.request())
    } catch (e: IOException) {
        throw e
    } catch (e: Exception) {
        throw IOException(e.message ?: e.javaClass.simpleName, e)
    }
}

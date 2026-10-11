@file:Suppress("unused")

package eu.kanade.tachiyomi.network

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.okio.decodeFromBufferedSource
import kotlinx.serialization.serializer
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import rx.Producer
import rx.Subscription
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

val jsonMime: MediaType = "application/json; charset=utf-8".toMediaType()

/** RxJava 1 wrapper used by lib-1.4 sources. Executes lazily and cancels on unsubscribe. */
fun Call.asObservable(): Observable<Response> = Observable.unsafeCreate { subscriber ->
    val call = clone()
    val requested = AtomicBoolean(false)
    val producer = object : Producer, Subscription {
        override fun request(n: Long) {
            if (n == 0L || !requested.compareAndSet(false, true)) return
            try {
                val response = call.execute()
                if (!subscriber.isUnsubscribed) {
                    subscriber.onNext(response)
                    subscriber.onCompleted()
                }
            } catch (e: Throwable) {
                if (!subscriber.isUnsubscribed) subscriber.onError(e)
            }
        }

        override fun unsubscribe() = call.cancel()
        override fun isUnsubscribed(): Boolean = call.isCanceled()
    }
    subscriber.add(producer)
    subscriber.setProducer(producer)
}

fun Call.asObservableSuccess(): Observable<Response> = asObservable().doOnNext { response ->
    if (!response.isSuccessful) {
        response.close()
        throw HttpException(response.code)
    }
}

suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    enqueue(
        object : Callback {
            override fun onResponse(call: Call, response: Response) {
                cont.resume(response) { _, value, _ -> value.close() }
            }

            override fun onFailure(call: Call, e: IOException) {
                if (!cont.isCancelled) cont.resumeWithException(e)
            }
        },
    )
    cont.invokeOnCancellation { runCatching { cancel() } }
}

suspend fun Call.awaitSuccess(): Response {
    val response = await()
    if (!response.isSuccessful) {
        response.close()
        throw HttpException(response.code)
    }
    return response
}

/** Like newCall but skips the cache and reports download progress. */
fun OkHttpClient.newCachelessCallWithProgress(request: Request, listener: ProgressListener): Call {
    val progressClient = newBuilder()
        .cache(null)
        .addNetworkInterceptor { chain ->
            val original = chain.proceed(chain.request())
            original.newBuilder().body(ProgressResponseBody(original.body, listener)).build()
        }
        .build()
    return progressClient.newCall(request)
}

inline fun <reified T> Response.parseAs(): T = decodeFromJsonResponse(serializer(), this)

fun <T> decodeFromJsonResponse(deserializer: DeserializationStrategy<T>, response: Response): T =
    response.body.source().use { Injekt.get<Json>().decodeFromBufferedSource(deserializer, it) }

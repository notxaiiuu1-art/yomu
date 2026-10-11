package eu.kanade.tachiyomi.util

import kotlinx.coroutines.suspendCancellableCoroutine
import rx.Observable
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Waits for the single value of a lib-1.4 RxJava observable inside a coroutine. */
internal suspend fun <T> Observable<T>.awaitSingle(): T = suspendCancellableCoroutine { cont ->
    val subscription = single().subscribe(
        { value -> if (cont.isActive) cont.resume(value) },
        { error -> if (cont.isActive) cont.resumeWithException(error) },
    )
    cont.invokeOnCancellation { subscription.unsubscribe() }
}

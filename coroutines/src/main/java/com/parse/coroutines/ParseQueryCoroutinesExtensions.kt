@file:JvmName("ParseQueryCoroutinesExtensions")

package com.parse.coroutines

import com.parse.ParseObject
import com.parse.ParseQuery
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

suspend fun <T : ParseObject> ParseQuery<T>.suspendFind(): List<T> {
    return findInternal()
}

internal suspend fun <T : ParseObject> ParseQuery<T>.findInternal(): List<T> {
    return suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation {
            cancel()
        }

        findInBackground { objects, e ->
            if (e == null) continuation.resume(objects)
            else continuation.resumeWithException(e)
        }
    }
}

suspend fun <T : ParseObject> ParseQuery<T>.getById(id: String): T {
    return getInternal(id)
}

internal suspend fun <T : ParseObject> ParseQuery<T>.getInternal(id: String): T {
    return suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation {
            cancel()
        }

        getInBackground(id) { obj, e ->
            if (e == null) continuation.resume(obj)
            else continuation.resumeWithException(e)
        }
    }
}

suspend fun <T : ParseObject> ParseQuery<T>.first(): T {
    return firstInternal()
}

internal suspend fun <T : ParseObject> ParseQuery<T>.firstInternal(): T {
    return suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation {
            cancel()
        }

        getFirstInBackground { obj, e ->
            if (e == null) continuation.resume(obj)
            else continuation.resumeWithException(e)
        }
    }
}

suspend fun <T : ParseObject> ParseQuery<T>.suspendCount(): Int {
    return countInternal()
}

internal suspend fun <T : ParseObject> ParseQuery<T>.countInternal(): Int {
    return suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation {
            cancel()
        }

        countInBackground { count, e ->
            if (e == null) continuation.resume(count)
            else continuation.resumeWithException(e)
        }
    }
}

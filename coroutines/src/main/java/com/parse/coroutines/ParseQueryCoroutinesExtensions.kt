@file:JvmName("ParseQueryCoroutinesExtensions")
@file:Suppress("EXTENSION_SHADOWED_BY_MEMBER")

package com.parse.coroutines

import com.parse.ParseObject
import com.parse.ParseQuery
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

suspend fun <T : ParseObject> ParseQuery<T>.find(): List<T> {
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

suspend fun <T : ParseObject> ParseQuery<T>.get(id: String): T {
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

suspend fun <T : ParseObject> ParseQuery<T>.count(): Int {
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
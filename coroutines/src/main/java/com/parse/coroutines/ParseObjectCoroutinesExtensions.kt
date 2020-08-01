@file:JvmName("ParseObjectCoroutinesExtensions")

package com.parse.coroutines

import com.parse.ParseObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

suspend fun <T : ParseObject> ParseObject.suspendFetch(): T {
    return suspendCoroutine { continuation ->

        fetchInBackground<T> { obj, e ->
            if (e == null) continuation.resume(obj)
            else continuation.resumeWithException(e)
        }
    }
}

suspend fun <T : ParseObject> ParseObject.suspendFetchIfNeeded(): T {
    return suspendCoroutine { continuation ->

        fetchIfNeededInBackground<T> { obj, e ->
            if (e == null) continuation.resume(obj)
            else continuation.resumeWithException(e)
        }
    }
}

suspend fun <T : ParseObject> ParseObject.fetchFromLocal(): T {
    return suspendCoroutine { continuation ->

        fetchFromLocalDatastoreInBackground<T> { obj, e ->
            if (e == null) continuation.resume(obj)
            else continuation.resumeWithException(e)
        }
    }
}

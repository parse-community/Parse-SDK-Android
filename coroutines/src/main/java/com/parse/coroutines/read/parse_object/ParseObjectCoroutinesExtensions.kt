@file:JvmName("ParseObjectCoroutinesExtensions")
@file:Suppress("EXTENSION_SHADOWED_BY_MEMBER")

package com.parse.coroutines.read.parse_object

import com.parse.ParseObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

suspend fun <T : ParseObject> ParseObject.fetch(): T {
    return suspendCoroutine { continuation ->

        fetchInBackground<T> { obj, e ->
            if (e == null) continuation.resume(obj)
            else continuation.resumeWithException(e)
        }
    }
}

suspend fun <T : ParseObject> ParseObject.fetchIfNeeded(): T {
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
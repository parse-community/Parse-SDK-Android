@file:JvmName("ParseObjectCoroutinesWriteExtensions")

package com.parse.coroutines.write.parse_object

import com.parse.ParseObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

suspend fun ParseObject.coroutineSave() {
    return suspendCoroutine { continuation ->
        saveInBackground {
            if (it == null) continuation.resume(Unit)
            else continuation.resumeWithException(it)
        }
    }
}

suspend fun ParseObject.coroutinePin() {
    return suspendCoroutine { continuation ->
        pinInBackground {
            if (it == null) continuation.resume(Unit)
            else continuation.resumeWithException(it)
        }
    }
}

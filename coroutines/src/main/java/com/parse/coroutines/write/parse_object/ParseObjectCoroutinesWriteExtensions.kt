@file:JvmName("ParseObjectCoroutinesWriteExtensions")
@file:Suppress("EXTENSION_SHADOWED_BY_MEMBER")

package com.parse.coroutines.write.parse_object

import com.parse.ParseObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

suspend fun ParseObject.save() {
    return suspendCoroutine { continuation ->
        saveInBackground {
            if (it == null) continuation.resume(Unit)
            else continuation.resumeWithException(it)
        }
    }
}

suspend fun ParseObject.pin() {
    return suspendCoroutine { continuation ->
        pinInBackground {
            if (it == null) continuation.resume(Unit)
            else continuation.resumeWithException(it)
        }
    }
}
@file:JvmName("ParseObjectCoroutinesWriteExtensions")

package com.parse.coroutines

import com.parse.ParseObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

suspend fun ParseObject.suspendSave() {
    return suspendCoroutine { continuation ->
        saveInBackground {
            if (it == null) continuation.resume(Unit)
            else continuation.resumeWithException(it)
        }
    }
}

suspend fun ParseObject.suspendPin() {
    return suspendCoroutine { continuation ->
        pinInBackground {
            if (it == null) continuation.resume(Unit)
            else continuation.resumeWithException(it)
        }
    }
}

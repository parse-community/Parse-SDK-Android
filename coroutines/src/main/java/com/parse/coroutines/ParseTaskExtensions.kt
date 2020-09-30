@file:JvmName("ParseTaskExtensions")
@file:Suppress("unused")

package com.parse.coroutines

import com.parse.boltsinternal.Task
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

@Suppress("BlockingMethodInNonBlockingContext")
suspend fun <T> Task<T>.suspendGet(dispatcher: CoroutineDispatcher = Dispatchers.IO) = withContext<T>(dispatcher) {
    return@withContext suspendCoroutine { continuation ->
        waitForCompletion()
        if (isFaulted) {
            continuation.resumeWithException(error)
        }
        continuation.resume(result)
    }
}

@Suppress("BlockingMethodInNonBlockingContext")
suspend fun Task<Void>.suspendRun(dispatcher: CoroutineDispatcher = Dispatchers.IO) = withContext<Unit>(dispatcher) {
    return@withContext suspendCoroutine { continuation ->
        waitForCompletion()
        if (isFaulted) {
            continuation.resumeWithException(error)
        }
        continuation.resume(Unit)
    }
}


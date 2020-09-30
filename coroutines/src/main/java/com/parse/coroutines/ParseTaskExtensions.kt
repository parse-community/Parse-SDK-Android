@file:JvmName("ParseTaskExtensions")

package com.parse.coroutines

import com.parse.boltsinternal.Task
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

@Suppress("BlockingMethodInNonBlockingContext")
suspend fun <T> Task<T>.suspendGet(dispatcher: CoroutineDispatcher = Dispatchers.IO) = withContext<T>(dispatcher) {
    suspendCoroutine { continuation ->
        if (isFaulted) {
            continuation.resumeWithException(error)
        }
        waitForCompletion()
    }
}

@Suppress("BlockingMethodInNonBlockingContext")
suspend fun Task<Void>.suspendRun(dispatcher: CoroutineDispatcher = Dispatchers.IO) = withContext<Unit>(dispatcher) {
    suspendCoroutine { continuation ->
        if (isFaulted) {
            continuation.resumeWithException(error)
        }
        waitForCompletion()
    }
}


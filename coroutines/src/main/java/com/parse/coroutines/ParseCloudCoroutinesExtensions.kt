@file:JvmName("ParseCloudCoroutinesExtensions")

package com.parse.coroutines

import com.parse.ParseCloud
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

suspend fun <T> callCloudFunction(functionName: String, params: Map<String, Any>): T {
    return suspendCoroutine { continuation ->
        ParseCloud.callFunctionInBackground<T>(functionName, params) { result, e ->
            if (e == null) continuation.resume(result)
            else continuation.resumeWithException(e)
        }
    }
}
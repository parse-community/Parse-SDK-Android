@file:JvmName("ParseQueryCoroutinesBuilder")
@file:Suppress("EXTENSION_SHADOWED_BY_MEMBER")

package com.parse.coroutines.read.query

import com.parse.ParseObject
import com.parse.ParseQuery
import com.parse.coroutines.read.ParseQueryOperation
import com.parse.coroutines.read.ParseQueryOperationImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

fun <T : ParseObject> CoroutineScope.launchQuery(
    query: ParseQuery<T>,
    context: CoroutineContext = EmptyCoroutineContext,
    block: suspend ParseQueryOperation<T>.() -> Unit
) : Job {
    return launch(context) {
        block.invoke(ParseQueryOperationImpl(query))
    }
}
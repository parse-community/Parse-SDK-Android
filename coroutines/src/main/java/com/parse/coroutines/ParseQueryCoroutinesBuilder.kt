@file:JvmName("ParseQueryCoroutinesBuilder")

package com.parse.coroutines

import com.parse.ParseObject
import com.parse.ParseQuery
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

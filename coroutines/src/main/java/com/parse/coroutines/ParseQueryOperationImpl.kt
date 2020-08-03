package com.parse.coroutines

import com.parse.ParseObject
import com.parse.ParseQuery

class ParseQueryOperationImpl<T : ParseObject>(private val query: ParseQuery<T>) : ParseQueryOperation<T> {

    override suspend fun find(): List<T> = query.findInternal()

    override suspend fun get(id: String): T = query.getInternal(id)

    override suspend fun first(): T = query.firstInternal()

    override suspend fun count(): Int = query.countInternal()
}

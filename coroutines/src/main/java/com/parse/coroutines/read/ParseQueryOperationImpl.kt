package com.parse.coroutines.read

import com.parse.ParseObject
import com.parse.ParseQuery
import com.parse.coroutines.read.query.countInternal
import com.parse.coroutines.read.query.findInternal
import com.parse.coroutines.read.query.firstInternal
import com.parse.coroutines.read.query.getInternal

class ParseQueryOperationImpl<T : ParseObject>(private val query: ParseQuery<T>) : ParseQueryOperation<T> {

    override suspend fun find(): List<T> = query.findInternal()

    override suspend fun get(id: String): T = query.getInternal(id)

    override suspend fun first(): T = query.firstInternal()

    override suspend fun count(): Int = query.countInternal()
}
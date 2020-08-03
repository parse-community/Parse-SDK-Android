package com.parse.coroutines

interface ParseQueryOperation<out T> {
    suspend fun find(): List<T>
    suspend fun get(id: String): T
    suspend fun first(): T
    suspend fun count(): Int
}

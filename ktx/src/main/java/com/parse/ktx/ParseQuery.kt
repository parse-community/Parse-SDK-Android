@file:Suppress("NOTHING_TO_INLINE", "unused")

package com.parse.ktx

import com.parse.ParseException
import com.parse.ParseObject
import com.parse.ParseQuery

/**
 * Parse hard limits [ParseQuery.find] to [ParseQuery.MAX_LIMIT] objects. This will fetch absolutely all the
 * objects. Use with caution, since you could potentially run out of memory if your query is too large.
 * Note that this will modify the current limit of the query
 */
@Throws(ParseException::class)
inline fun <T: ParseObject> ParseQuery<T>.findAll(): List<T> {
    limit = ParseQuery.MAX_LIMIT
    val list = mutableListOf<T>()
    try {
        while (true) {
            val result = find()
            list.addAll(result)
            if (result.size < ParseQuery.MAX_LIMIT) {
                break
            } else {
                setSkip(skip + ParseQuery.MAX_LIMIT)
            }
        }
        return list
    } catch (ex : ParseException) {
        if (ex.code == ParseException.OBJECT_NOT_FOUND) {
            return list
        }
        throw ex
    }
}
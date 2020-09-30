@file:Suppress("unused", "NOTHING_TO_INLINE")

package com.parse.ktx.delegates

import com.parse.ParseObject
import com.parse.ParseRelation
import kotlin.reflect.KProperty

/**
 * A [ParseRelation] property delegation for [ParseObject].
 */
class ParseRelationDelegate<T : ParseObject>(private val name: String?) {

    operator fun getValue(parseObject: ParseObject, property: KProperty<*>): ParseRelation<T> {
        return parseObject.getRelation<T>(name ?: property.name)
    }

}

/**
 * Returns a [ParseRelation] property delegate for [ParseObject]s.
 * This uses [ParseObject.getRelation].
 */
inline fun <T : ParseObject> relationAttribute(name: String? = null) = ParseRelationDelegate<T>(name)

@file:Suppress("unused", "NOTHING_TO_INLINE")

package com.parse.ktx.delegates

import com.parse.ParseObject
import com.parse.ParseRelation
import kotlin.reflect.KProperty

/**
 * A [ParseRelation] property delegation for [ParseObject].
 */
class ParseRelationDelegate<T : ParseObject> {

    operator fun getValue(parseObject: ParseObject, property: KProperty<*>): ParseRelation<T> {
        return parseObject.getRelation<T>(property.name)
    }

}

/**
 * Returns a [ParseRelation] property delegate for [ParseObject]s.
 * This uses [ParseObject.getRelation].
 */
inline fun <T : ParseObject> relationAttribute() = ParseRelationDelegate<T>()

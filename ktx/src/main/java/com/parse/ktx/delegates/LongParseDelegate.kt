@file:Suppress("unused", "NOTHING_TO_INLINE")

package com.parse.ktx.delegates

import com.parse.ParseObject
import kotlin.reflect.KProperty

/**
 * A [Long] property delegation for [ParseObject].
 */
class LongParseDelegate(private val name: String?) {

    operator fun getValue(parseObject: ParseObject, property: KProperty<*>): Long {
        return parseObject.getLong(name ?: property.name)
    }

    operator fun setValue(parseObject: ParseObject, property: KProperty<*>, value: Long) {
        parseObject.put(name ?: property.name, value)
    }

}

/**
 * Returns a [Long] property delegate for [ParseObject]s. This uses [ParseObject.getLong]
 * and [ParseObject.put].
 */
inline fun longAttribute(name: String? = null) = LongParseDelegate(name)

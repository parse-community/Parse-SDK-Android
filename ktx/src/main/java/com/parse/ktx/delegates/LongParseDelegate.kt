@file:Suppress("unused", "NOTHING_TO_INLINE")

package com.parse.ktx.delegates

import com.parse.ParseObject
import kotlin.reflect.KProperty

/**
 * A [Long] property delegation for [ParseObject].
 */
class LongParseDelegate {

    operator fun getValue(parseObject: ParseObject, property: KProperty<*>): Long {
        return parseObject.getLong(property.name)
    }

    operator fun setValue(parseObject: ParseObject, property: KProperty<*>, value: Long) {
        parseObject.put(property.name, value)
    }

}

/**
 * Returns a [Long] property delegate for [ParseObject]s. This uses [ParseObject.getLong]
 * and [ParseObject.put].
 */
inline fun longAttribute() = LongParseDelegate()

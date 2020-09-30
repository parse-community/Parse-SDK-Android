@file:Suppress("unused", "NOTHING_TO_INLINE")

package com.parse.ktx.delegates

import com.parse.ParseObject
import kotlin.reflect.KProperty

/**
 * A [Boolean] property delegation for [ParseObject].
 */
class BooleanParseDelegate(private val name: String?) {

    operator fun getValue(parseObject: ParseObject, property: KProperty<*>): Boolean {
        return parseObject.getBoolean(name ?: property.name)
    }

    operator fun setValue(parseObject: ParseObject, property: KProperty<*>, value: Boolean) {
        parseObject.put(name ?: property.name, value)
    }

}

/**
 * Returns a [Boolean] property delegate for [ParseObject]s. This uses [ParseObject.getBoolean]
 * and [ParseObject.put].
 */
inline fun booleanAttribute(name: String? = null) = BooleanParseDelegate(name)

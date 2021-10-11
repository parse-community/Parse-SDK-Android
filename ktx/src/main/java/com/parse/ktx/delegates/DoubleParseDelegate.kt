@file:Suppress("unused", "NOTHING_TO_INLINE")

package com.parse.ktx.delegates

import com.parse.ParseObject
import kotlin.reflect.KProperty

/**
 * A [Double] property delegation for [ParseObject].
 */
class DoubleParseDelegate(private val name: String?) {

    operator fun getValue(parseObject: ParseObject, property: KProperty<*>): Double {
        return parseObject.getDouble(name ?: property.name)
    }

    operator fun setValue(parseObject: ParseObject, property: KProperty<*>, value: Double) {
        parseObject.put(name ?: property.name, value)
    }

}

/**
 * Returns a [Double] property delegate for [ParseObject]s. This uses [ParseObject.getDouble]
 * and [ParseObject.put].
 */
inline fun doubleAttribute(name: String? = null) = DoubleParseDelegate(name)

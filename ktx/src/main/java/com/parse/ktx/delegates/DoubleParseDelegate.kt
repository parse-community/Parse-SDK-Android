@file:Suppress("unused", "NOTHING_TO_INLINE")

package com.parse.ktx.delegates

import com.parse.ParseObject
import kotlin.reflect.KProperty

/**
 * A [Double] property delegation for [ParseObject].
 */
class DoubleParseDelegate {

    operator fun getValue(parseObject: ParseObject, property: KProperty<*>): Double {
        return parseObject.getDouble(property.name)
    }

    operator fun setValue(parseObject: ParseObject, property: KProperty<*>, value: Double) {
        parseObject.put(property.name, value)
    }

}

/**
 * Returns a [Double] property delegate for [ParseObject]s. This uses [ParseObject.getDouble]
 * and [ParseObject.put].
 */
inline fun doubleAttribute() = DoubleParseDelegate()

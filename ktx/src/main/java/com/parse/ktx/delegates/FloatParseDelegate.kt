@file:Suppress("unused", "NOTHING_TO_INLINE")

package com.parse.ktx.delegates

import com.parse.ParseObject
import kotlin.reflect.KProperty

/**
 * A [Float] property delegation for [ParseObject].
 */
class FloatParseDelegate {

    operator fun getValue(parseObject: ParseObject, property: KProperty<*>): Float {
        return parseObject.getDouble(property.name).toFloat()
    }

    operator fun setValue(parseObject: ParseObject, property: KProperty<*>, value: Float) {
        parseObject.put(property.name, value)
    }

}

/**
 * Returns a [Float] property delegate for [ParseObject]s. This uses a custom implementation for get
 * and [ParseObject.put].
 */
inline fun floatAttribute() = FloatParseDelegate()

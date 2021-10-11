@file:Suppress("unused", "NOTHING_TO_INLINE")

package com.parse.ktx.delegates

import com.parse.ParseObject
import kotlin.reflect.KProperty

/**
 * A [Float] property delegation for [ParseObject].
 */
class FloatParseDelegate(private val name: String?) {

    operator fun getValue(parseObject: ParseObject, property: KProperty<*>): Float {
        return parseObject.getDouble(name ?: property.name).toFloat()
    }

    operator fun setValue(parseObject: ParseObject, property: KProperty<*>, value: Float) {
        parseObject.put(name ?: property.name, value)
    }

}

/**
 * Returns a [Float] property delegate for [ParseObject]s. This uses a custom implementation for get
 * and [ParseObject.put].
 */
inline fun floatAttribute(name: String? = null) = FloatParseDelegate(name)

@file:Suppress("unused", "NOTHING_TO_INLINE")

package com.parse.ktx.delegates

import com.parse.ParseObject
import kotlin.reflect.KProperty

/**
 * A [Int] property delegation for [ParseObject].
 */
class IntParseDelegate(private val name: String?) {

    operator fun getValue(parseObject: ParseObject, property: KProperty<*>): Int {
        return parseObject.getInt(name ?: property.name)
    }

    operator fun setValue(parseObject: ParseObject, property: KProperty<*>, value: Int) {
        parseObject.put(name ?:property.name, value)
    }

}

/**
 * Returns a [Int] property delegate for [ParseObject]s. This uses [ParseObject.getInt]
 * and [ParseObject.put].
 */
inline fun intAttribute(name: String? = null) = IntParseDelegate(name)

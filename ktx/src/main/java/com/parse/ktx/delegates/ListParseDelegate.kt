@file:Suppress("unused", "NOTHING_TO_INLINE")

package com.parse.ktx.delegates

import com.parse.ParseObject
import com.parse.ktx.putOrIgnore
import kotlin.reflect.KProperty

/**
 * A [List] property delegation for [ParseObject].
 */
class ListParseDelegate<T>(private val name: String?) {

    operator fun getValue(parseObject: ParseObject, property: KProperty<*>): MutableList<T>? {
        return parseObject.getList<T>(name ?: property.name) as? MutableList<T>
    }

    operator fun setValue(parseObject: ParseObject, property: KProperty<*>, value: MutableList<T>?) {
        parseObject.putOrIgnore(name ?: property.name, value)
    }

}

/**
 * Returns a [List] property delegate for [ParseObject]s. This uses [ParseObject.getList]
 * and [ParseObject.putOrIgnore].
 */
inline fun <T> listAttribute(name: String? = null) = ListParseDelegate<T>(null)

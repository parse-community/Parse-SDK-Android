@file:Suppress("unused", "NOTHING_TO_INLINE")

package com.parse.ktx.delegates

import com.parse.ParseObject
import com.parse.ktx.putOrIgnore
import kotlin.reflect.KProperty

/**
 * A [Map] property delegation for [ParseObject].
 */
class MapParseDelegate<V>(private val name: String?) {

    operator fun getValue(parseObject: ParseObject, property: KProperty<*>): MutableMap<String, V>? {
        return parseObject.getMap<V>(name ?: property.name) as? MutableMap<String, V>
    }

    operator fun setValue(parseObject: ParseObject, property: KProperty<*>, value: MutableMap<String, V>?) {
        parseObject.putOrIgnore(name ?: property.name, value)
    }

}

/**
 * Returns a [Map] property delegate for [ParseObject]s. This uses [ParseObject.getMap]
 * and [ParseObject.putOrIgnore].
 */
inline fun <V> mapAttribute(name: String? = null) = MapParseDelegate<V>(name)

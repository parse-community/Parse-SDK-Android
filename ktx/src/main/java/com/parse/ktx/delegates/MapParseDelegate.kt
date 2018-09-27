@file:Suppress("unused", "NOTHING_TO_INLINE")

package com.parse.ktx.delegates

import com.parse.ParseObject
import com.parse.ktx.putOrIgnore
import kotlin.reflect.KProperty

/**
 * Created by daniel on 9/26/18
 */
class MapParseDelegate<V> {

    operator fun getValue(parseObject: ParseObject, property: KProperty<*>): MutableMap<String, V>? {
        return parseObject.getMap<V>(property.name) as? MutableMap<String, V>
    }

    operator fun setValue(parseObject: ParseObject, property: KProperty<*>, value: MutableMap<String, V>?) {
        parseObject.putOrIgnore(property.name, value)
    }

}

inline fun <V> mapAttribute() = MapParseDelegate<V>()

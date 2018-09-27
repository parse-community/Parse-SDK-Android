@file:Suppress("unused", "NOTHING_TO_INLINE")

package com.parse.ktx.delegates

import com.parse.ParseObject
import kotlin.reflect.KProperty

/**
 * Created by daniel on 9/26/18
 */
class IntParseDelegate {

    operator fun getValue(parseObject: ParseObject, property: KProperty<*>): Int {
        return parseObject.getInt(property.name)
    }

    operator fun setValue(parseObject: ParseObject, property: KProperty<*>, value: Int) {
        parseObject.put(property.name, value)
    }

}

inline fun intAttribute() = IntParseDelegate()

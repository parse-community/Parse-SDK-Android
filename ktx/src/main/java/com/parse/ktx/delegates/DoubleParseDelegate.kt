@file:Suppress("unused", "NOTHING_TO_INLINE")

package com.parse.ktx.delegates

import com.parse.ParseObject
import kotlin.reflect.KProperty

/**
 * Created by daniel on 9/26/18
 */
class DoubleParseDelegate {

    operator fun getValue(parseObject: ParseObject, property: KProperty<*>): Double {
        return parseObject.getDouble(property.name)
    }

    operator fun setValue(parseObject: ParseObject, property: KProperty<*>, value: Double) {
        parseObject.put(property.name, value)
    }

}

inline fun doubleAttribute() = DoubleParseDelegate()

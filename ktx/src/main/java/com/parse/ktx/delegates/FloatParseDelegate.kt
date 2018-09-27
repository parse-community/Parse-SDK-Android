@file:Suppress("unused", "NOTHING_TO_INLINE")

package com.parse.ktx.delegates

import com.parse.ParseObject
import kotlin.reflect.KProperty

/**
 * Created by daniel on 9/26/18
 */
class FloatParseDelegate {

    operator fun getValue(parseObject: ParseObject, property: KProperty<*>): Float {
        return parseObject.getDouble(property.name).toFloat()
    }

    operator fun setValue(parseObject: ParseObject, property: KProperty<*>, value: Float) {
        parseObject.put(property.name, value)
    }

}

inline fun floatAttribute() = FloatParseDelegate()

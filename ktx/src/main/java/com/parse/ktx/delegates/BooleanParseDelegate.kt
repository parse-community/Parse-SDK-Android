@file:Suppress("unused", "NOTHING_TO_INLINE")

package com.parse.ktx.delegates

import com.parse.ParseObject
import kotlin.reflect.KProperty

/**
 * Created by daniel on 9/26/18
 */
class BooleanParseDelegate {

    operator fun getValue(parseObject: ParseObject, property: KProperty<*>): Boolean {
        return parseObject.getBoolean(property.name)
    }

    operator fun setValue(parseObject: ParseObject, property: KProperty<*>, boolean: Boolean) {
        parseObject.put(property.name, boolean)
    }

}

inline fun booleanAttribute() = BooleanParseDelegate()

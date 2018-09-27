@file:Suppress("unused", "NOTHING_TO_INLINE")

package com.parse.ktx.delegates

import com.parse.ParseObject
import com.parse.ktx.getAs
import kotlin.reflect.KProperty

/**
 * Created by daniel on 9/26/18
 */
class StringParseDelegate<S : String?>(private val filter: (String) -> String) {

    operator fun getValue(parseObject: ParseObject, property: KProperty<*>): S {
        return parseObject.getAs(property.name)
    }

    operator fun setValue(parseObject: ParseObject, property: KProperty<*>, value: S) {
        if (value != null) {
            parseObject.put(property.name, filter.invoke(value))
        }
    }

}

inline fun stringAttribute(
    noinline filter: (String) -> String = { it }
) = StringParseDelegate<String?>(filter)

inline fun nonNullStringAttribute(
    noinline filter: (String) -> String = { it }
) = StringParseDelegate<String>(filter)

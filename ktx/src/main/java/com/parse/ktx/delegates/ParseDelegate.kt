@file:Suppress("unused", "NOTHING_TO_INLINE")

package com.parse.ktx.delegates

import com.parse.ParseObject
import com.parse.ktx.getAs
import com.parse.ktx.putOrIgnore
import kotlin.reflect.KProperty

/**
 * Created by daniel on 9/26/18
 */
class ParseDelegate<T> {

    @Suppress("UNCHECKED_CAST")
    operator fun getValue(parseObject: ParseObject, property: KProperty<*>): T {
        return parseObject.getAs(property.name)
    }

    operator fun setValue(parseObject: ParseObject, property: KProperty<*>, value: T?) {
        parseObject.putOrIgnore(property.name, value)
    }

}

inline fun <T> attribute() = ParseDelegate<T>()

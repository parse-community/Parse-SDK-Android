@file:Suppress("unused", "NOTHING_TO_INLINE")

package com.parse.ktx.delegates

import com.parse.ParseObject
import com.parse.ktx.getAs
import com.parse.ktx.putOrIgnore
import kotlin.reflect.KProperty

/**
 * A generic property delegation for [ParseObject].
 */
class ParseDelegate<T> {

    @Suppress("UNCHECKED_CAST")
    operator fun getValue(parseObject: ParseObject, property: KProperty<*>): T {
        return parseObject.getAs(property.name)
    }

    operator fun setValue(parseObject: ParseObject, property: KProperty<*>, value: T) {
        parseObject.putOrIgnore(property.name, value)
    }

}

/**
 * Returns a generic property delegate for [ParseObject]s. This uses [ParseObject.getAs]
 * and [ParseObject.putOrIgnore].
 */
inline fun <T> attribute() = ParseDelegate<T>()

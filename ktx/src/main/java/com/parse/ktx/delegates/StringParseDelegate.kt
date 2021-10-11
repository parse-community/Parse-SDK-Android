@file:Suppress("unused", "NOTHING_TO_INLINE")

package com.parse.ktx.delegates

import com.parse.ParseObject
import com.parse.ktx.getAs
import kotlin.reflect.KProperty

/**
 * A [String] property delegation for [ParseObject].
 */
class StringParseDelegate<S : String?>(
    private val name: String?,
    private val filter: (String) -> String
) {

    operator fun getValue(parseObject: ParseObject, property: KProperty<*>): S {
        return parseObject.getAs(name ?: property.name)
    }

    operator fun setValue(parseObject: ParseObject, property: KProperty<*>, value: S) {
        if (value != null) {
            parseObject.put(name ?: property.name, filter.invoke(value))
        }
    }

}

/**
 * Returns a nullable [String] property delegate for [ParseObject]s. This uses [ParseObject.getAs]
 * and a custom implementation for set.
 */
inline fun nullableStringAttribute(
    name: String? = null,
    noinline filter: (String) -> String = { it }
) = StringParseDelegate<String?>(name, filter)

/**
 * Returns a [String] property delegate for [ParseObject]s. This uses [ParseObject.getAs]
 * and a custom implementation for set.
 */
inline fun stringAttribute(
    name: String? = null,
    noinline filter: (String) -> String = { it }
) = StringParseDelegate<String>(name, filter)

@file:Suppress("unused", "NOTHING_TO_INLINE")

package com.parse.ktx.delegates

import com.parse.ParseObject
import com.parse.ktx.putOrRemove
import org.json.JSONObject
import kotlin.reflect.KProperty

/**
 * A generic property delegation for [ParseObject], and give some checks to avoid throw some
 * exceptions.
 */
class SafeParseDelegate<T> {

    @Suppress("UNCHECKED_CAST")
    operator fun getValue(parseObject: ParseObject, property: KProperty<*>): T? {
        val value = if (parseObject.has(property.name)) {
            parseObject.get(property.name)
        } else {
            null
        }
        return if (JSONObject.NULL == value) {
            null
        } else {
            value as? T?
        }
    }

    operator fun setValue(parseObject: ParseObject, property: KProperty<*>, t: T?) {
        parseObject.putOrRemove(property.name, t)
    }

}

/**
 * Returns a generic property delegate for [ParseObject]s. This uses a custom get implementation
 * and [ParseObject.putOrRemove].
 */
inline fun <T> safeAttribute() = SafeParseDelegate<T>()

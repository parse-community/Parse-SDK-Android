@file:Suppress("unused", "NOTHING_TO_INLINE")

package com.parse.ktx.delegates

import com.parse.ParseObject
import com.parse.ktx.putOrRemove
import org.json.JSONObject
import kotlin.reflect.KProperty

/**
 * Created by daniel on 9/27/18
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

inline fun <T> safeAttribute() = SafeParseDelegate<T>()

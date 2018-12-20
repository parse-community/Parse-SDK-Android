@file:Suppress("unused", "NOTHING_TO_INLINE")

package com.parse.ktx.delegates

import com.parse.ParseObject
import com.parse.ktx.putOrIgnore
import org.json.JSONObject
import kotlin.reflect.KProperty

/**
 * A [JSONObject] property delegation for [ParseObject].
 */
class JsonObjectParseDelegate {

    operator fun getValue(parseObject: ParseObject, property: KProperty<*>): JSONObject? {
        return parseObject.getJSONObject(property.name)
    }

    operator fun setValue(parseObject: ParseObject, property: KProperty<*>, value: JSONObject?) {
        parseObject.putOrIgnore(property.name, value)
    }

}

/**
 * Returns a [JSONObject] property delegate for [ParseObject]s. This uses [ParseObject.getJSONObject]
 * and [ParseObject.putOrIgnore].
 */
fun jsonObjectAttribute() = JsonObjectParseDelegate()

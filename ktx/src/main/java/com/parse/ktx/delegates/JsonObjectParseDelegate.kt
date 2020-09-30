@file:Suppress("unused", "NOTHING_TO_INLINE")

package com.parse.ktx.delegates

import com.parse.ParseObject
import com.parse.ktx.putOrIgnore
import org.json.JSONObject
import kotlin.reflect.KProperty

/**
 * A [JSONObject] property delegation for [ParseObject].
 */
class JsonObjectParseDelegate(private val name: String?) {

    operator fun getValue(parseObject: ParseObject, property: KProperty<*>): JSONObject? {
        return parseObject.getJSONObject(name ?: property.name)
    }

    operator fun setValue(parseObject: ParseObject, property: KProperty<*>, value: JSONObject?) {
        parseObject.putOrIgnore(name ?: property.name, value)
    }

}

/**
 * Returns a [JSONObject] property delegate for [ParseObject]s. This uses [ParseObject.getJSONObject]
 * and [ParseObject.putOrIgnore].
 */
fun jsonObjectAttribute(name: String? = null) = JsonObjectParseDelegate(name)

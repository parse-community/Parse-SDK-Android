@file:Suppress("unused", "NOTHING_TO_INLINE")

package com.parse.ktx.delegates

import com.parse.ParseObject
import com.parse.ktx.putOrIgnore
import org.json.JSONObject
import kotlin.reflect.KProperty

/**
 * Created by daniel on 9/26/18
 */
class JsonObjectParseDelegate {

    operator fun getValue(parseObject: ParseObject, property: KProperty<*>): JSONObject? {
        return parseObject.getJSONObject(property.name)
    }

    operator fun setValue(parseObject: ParseObject, property: KProperty<*>, value: JSONObject?) {
        parseObject.putOrIgnore(property.name, value)
    }

}

fun jsonObjectAttribute() = JsonObjectParseDelegate()

@file:Suppress("unused", "NOTHING_TO_INLINE")

package com.parse.ktx.delegates

import com.parse.ParseObject
import com.parse.ktx.putOrIgnore
import org.json.JSONArray
import kotlin.reflect.KProperty

/**
 * Created by daniel on 9/26/18
 */
class JsonArrayParseDelegate {

    operator fun getValue(parseObject: ParseObject, property: KProperty<*>): JSONArray? {
        return parseObject.getJSONArray(property.name)
    }

    operator fun setValue(parseObject: ParseObject, property: KProperty<*>, value: JSONArray?) {
        parseObject.putOrIgnore(property.name, value)
    }

}

inline fun jsonArrayAttribute() = JsonArrayParseDelegate()

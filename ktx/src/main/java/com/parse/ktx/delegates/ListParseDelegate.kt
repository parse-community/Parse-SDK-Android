@file:Suppress("unused", "NOTHING_TO_INLINE")

package com.parse.ktx.delegates

import com.parse.ParseObject
import com.parse.ktx.putOrIgnore
import kotlin.reflect.KProperty

/**
 * Created by daniel on 9/26/18
 */
class ListParseDelegate<T> {

    operator fun getValue(parseObject: ParseObject, property: KProperty<*>): MutableList<T>? {
        return parseObject.getList<T>(property.name) as? MutableList<T>
    }

    operator fun setValue(parseObject: ParseObject, property: KProperty<*>, value: MutableList<T>?) {
        parseObject.putOrIgnore(property.name, value)
    }

}

inline fun <T> listAttribute() = ListParseDelegate<T>()

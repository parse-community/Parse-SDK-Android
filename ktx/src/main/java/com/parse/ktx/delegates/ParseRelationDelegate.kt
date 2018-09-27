@file:Suppress("unused", "NOTHING_TO_INLINE")

package com.parse.ktx.delegates

import com.parse.ParseObject
import com.parse.ParseRelation
import kotlin.reflect.KProperty

/**
 * Created by daniel on 9/27/18
 */
class ParseRelationDelegate<T : ParseObject> {

    operator fun getValue(parseObject: ParseObject, property: KProperty<*>): ParseRelation<T> {
        return parseObject.getRelation<T>(property.name)
    }

}

inline fun <T : ParseObject> relationAttribute() = ParseRelationDelegate<T>()

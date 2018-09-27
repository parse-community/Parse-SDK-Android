@file:Suppress("unused")

package com.parse.ktx.delegates

import com.parse.ParseObject
import kotlin.reflect.KProperty

/**
 * Created by daniel on 9/27/18
 */
class EnumParseDelegate<T : Enum<T>>(private val default: T?, private val enumClass: Class<T>) {

    operator fun getValue(parseObject: ParseObject, property: KProperty<*>): T {
        return try {
            java.lang.Enum.valueOf(enumClass, parseObject.getString(property.name)!!.toUpperCase())
        } catch (e: Exception) {
            default ?: throw e
        }
    }

    operator fun setValue(parseObject: ParseObject, property: KProperty<*>, t: T) {
        parseObject.put(property.name, t.name.toLowerCase())
    }

}

inline fun <reified T : Enum<T>> enumAttribute(default: T? = null) = EnumParseDelegate(default, T::class.java)

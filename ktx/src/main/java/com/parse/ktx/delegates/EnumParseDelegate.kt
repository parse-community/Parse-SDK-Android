@file:Suppress("unused")

package com.parse.ktx.delegates

import com.parse.ParseObject
import kotlin.reflect.KProperty

/**
 * A [Enum] property delegation for [ParseObject].
 *
 * This implementation save enum's name in lower case on parse-server and when try retrieve it
 * convert again to upper case to find correspondent local enum.
 */
class EnumParseDelegate<T : Enum<T>>(
    private val name: String?,
    private val default: T?,
    private val enumClass: Class<T>,
) {
    operator fun getValue(
        parseObject: ParseObject,
        property: KProperty<*>,
    ): T {
        return try {
            java.lang.Enum.valueOf(
                enumClass,
                parseObject.getString(name ?: property.name)!!.uppercase(),
            )
        } catch (e: Exception) {
            default ?: throw e
        }
    }

    operator fun setValue(
        parseObject: ParseObject,
        property: KProperty<*>,
        t: T,
    ) {
        parseObject.put(name ?: property.name, t.name.lowercase())
    }
}

/**
 * Returns a [Enum] property delegate for [ParseObject]s. This uses custom implementation for get
 * to retrieve a local version of the your enum and [ParseObject.put].
 */
inline fun <reified T : Enum<T>> enumAttribute(default: T? = null) = EnumParseDelegate(null, default, T::class.java)

/**
 * Returns a [Enum] property delegate for [ParseObject]s. This uses custom implementation for get
 * to retrieve a local version of the your enum and [ParseObject.put].
 */
inline fun <reified T : Enum<T>> enumAttribute(
    name: String? = null,
    default: T? = null,
) = EnumParseDelegate(name, default, T::class.java)

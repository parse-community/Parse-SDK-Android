package com.parse.ktx.delegates

import com.parse.ParseObject
import com.parse.ktx.putOrIgnore
import kotlin.reflect.KProperty


/**
 * A [List] of [Enum] property delegation for [ParseObject].
 *
 * This implementation save list of enum values as a list of String in lower case on parse-server and when try retrieve it
 * convert again to upper case to find correspondent local enum.
 */
class ListOfEnumParseDelegate<T : Enum<T>>(private val enumClass: Class<T>) {

  operator fun getValue(parseObject: ParseObject, property: KProperty<*>): MutableList<T>? {
    val value = parseObject.getList<String>(property.name)
    return value?.map {
      java.lang.Enum.valueOf(enumClass, it.toUpperCase())
    } as? MutableList<T>
  }

  operator fun setValue(parseObject: ParseObject, property: KProperty<*>, value: MutableList<T>?) {
    parseObject.putOrIgnore(property.name, value?.map { it.name.toLowerCase() })
  }

}

/**
 * Returns a [List] of [Enum] property delegate for [ParseObject]s. This uses [ParseObject.getList]
 * and [ParseObject.putOrIgnore].
 */
inline fun <reified T : Enum<T>> listOfEnumAttribute() = ListOfEnumParseDelegate<T>(T::class.java)

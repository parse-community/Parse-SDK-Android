@file:Suppress("unused", "NOTHING_TO_INLINE")

package com.parse.ktx.delegates

import com.parse.ParseObject
import com.parse.ktx.putOrIgnore
import kotlin.reflect.KProperty

/**
 * A [ByteArray] property delegation for [ParseObject].
 */
class BytesParseDelegate(private val name: String?) {

    operator fun getValue(parseObject: ParseObject, property: KProperty<*>): ByteArray? {
        return parseObject.getBytes(name ?: property.name)
    }

    operator fun setValue(parseObject: ParseObject, property: KProperty<*>, value: ByteArray?) {
        parseObject.putOrIgnore(name ?:property.name, value)
    }

}

/**
 * Returns a [ByteArray] property delegate for [ParseObject]s. This uses [ParseObject.getBytes]
 * and [ParseObject.putOrIgnore].
 */
inline fun bytesAttribute(name: String? = null) = BytesParseDelegate(name)

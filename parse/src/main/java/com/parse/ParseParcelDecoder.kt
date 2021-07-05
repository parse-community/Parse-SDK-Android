/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse

import android.os.Parcel
import org.json.JSONObject
import java.util.*
import kotlin.collections.ArrayList

/**
 * A `ParseParcelableDecoder` can be used to unparcel objects such as
 * [com.parse.ParseObject] from a [android.os.Parcel].
 *
 *
 * This is capable of decoding objects and pointers to them.
 * However, for improved behavior in the case of [ParseObject]s, use the stateful
 * implementation [ParseObjectParcelDecoder].
 *
 * @see ParseParcelEncoder
 *
 * @see ParseObjectParcelDecoder
 */
/* package */
internal open class ParseParcelDecoder {
    fun decode(source: Parcel): Any? {
        val type = source.readString()
        return when (type) {
            ParseParcelEncoder.TYPE_OBJECT -> decodeParseObject(source)
            ParseParcelEncoder.TYPE_POINTER -> decodePointer(source)
            ParseParcelEncoder.TYPE_DATE -> {
                val iso = source.readString()
                ParseDateFormat.getInstance().parse(iso)
            }
            ParseParcelEncoder.TYPE_BYTES -> {
                val bytes = ByteArray(source.readInt())
                source.readByteArray(bytes)
                bytes
            }
            ParseParcelEncoder.TYPE_OP -> ParseFieldOperations.decode(source, this)
            ParseParcelEncoder.TYPE_FILE -> ParseFile(source, this)
            ParseParcelEncoder.TYPE_GEOPOINT -> ParseGeoPoint(source, this)
            ParseParcelEncoder.TYPE_POLYGON -> ParsePolygon(source, this)
            ParseParcelEncoder.TYPE_ACL -> ParseACL(source, this)
            ParseParcelEncoder.TYPE_RELATION -> ParseRelation<ParseObject>(source, this)
            ParseParcelEncoder.TYPE_MAP -> {
                val size = source.readInt()
                val map: MutableMap<String?, Any?> =
                    HashMap(size)
                var i = 0
                while (i < size) {
                    map[source.readString()] = decode(source)
                    i++
                }
                map
            }
            ParseParcelEncoder.TYPE_COLLECTION -> {
                val length = source.readInt()
                val list = ArrayList<Any?>(length)
                var i = 0
                while (i < length) {
                    list.add(i, decode(source))
                    i++
                }
                list
            }
            ParseParcelEncoder.TYPE_JSON_NULL -> JSONObject.NULL
            ParseParcelEncoder.TYPE_NULL -> null
            ParseParcelEncoder.TYPE_NATIVE -> source.readValue(null) // No need for a class loader.
            else -> throw RuntimeException("Could not unparcel objects from this Parcel.")
        }
    }

    protected fun decodeParseObject(source: Parcel?): ParseObject {
        return ParseObject.createFromParcel(source!!, this)
    }

    protected open fun decodePointer(source: Parcel): ParseObject? {
        // By default, use createWithoutData. Overriden in subclass.
        return ParseObject.createWithoutData(source.readString(), source.readString())
    }

    companion object {
        // This class isn't really a Singleton, but since it has no state, it's more efficient to get the
        // default instance.
        private val INSTANCE = ParseParcelDecoder()
        @JvmStatic
        fun get(): ParseParcelDecoder {
            return INSTANCE
        }
    }
}
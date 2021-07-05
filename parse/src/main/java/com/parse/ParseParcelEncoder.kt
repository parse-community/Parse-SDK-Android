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

/**
 * A `ParseParcelableEncoder` can be used to parcel objects into a [android.os.Parcel].
 *
 *
 * This is capable of parceling [ParseObject]s, but the result can likely be a
 * [StackOverflowError] due to circular references in the objects tree.
 * When needing to parcel [ParseObject], use the stateful [ParseObjectParcelEncoder].
 *
 * @see ParseParcelDecoder
 *
 * @see ParseObjectParcelEncoder
 */
/* package */
open class ParseParcelEncoder {
    fun encode(`object`: Any?, dest: Parcel) {
        try {
            if (`object` is ParseObject) {
                // By default, encode as a full ParseObject. Overriden in sublasses.
                encodeParseObject(`object`, dest)
            } else if (`object` is Date) {
                dest.writeString(TYPE_DATE)
                dest.writeString(ParseDateFormat.getInstance().format(`object` as Date?))
            } else if (`object` is ByteArray) {
                dest.writeString(TYPE_BYTES)
                val bytes = `object`
                dest.writeInt(bytes.size)
                dest.writeByteArray(bytes)
            } else if (`object` is ParseFieldOperation) {
                dest.writeString(TYPE_OP)
                `object`.encode(dest, this)
            } else if (`object` is ParseFile) {
                dest.writeString(TYPE_FILE)
                `object`.writeToParcel(dest, this)
            } else if (`object` is ParseGeoPoint) {
                dest.writeString(TYPE_GEOPOINT)
                `object`.writeToParcel(dest, this)
            } else if (`object` is ParsePolygon) {
                dest.writeString(TYPE_POLYGON)
                `object`.writeToParcel(dest, this)
            } else if (`object` is ParseACL) {
                dest.writeString(TYPE_ACL)
                `object`.writeToParcel(dest, this)
            } else if (`object` is ParseRelation<*>) {
                dest.writeString(TYPE_RELATION)
                `object`.writeToParcel(dest, this)
            } else if (`object` is Map<*, *>) {
                dest.writeString(TYPE_MAP)
                val map = `object` as Map<String, Any>
                dest.writeInt(map.size)
                for ((key, value) in map) {
                    dest.writeString(key)
                    encode(value, dest)
                }
            } else if (`object` is Collection<*>) {
                dest.writeString(TYPE_COLLECTION)
                dest.writeInt(`object`.size)
                for (item in `object`) {
                    encode(item, dest)
                }
            } else if (`object` === JSONObject.NULL) {
                dest.writeString(TYPE_JSON_NULL)
            } else if (`object` == null) {
                dest.writeString(TYPE_NULL)

                // String, Number, Boolean. Simply use writeValue
            } else if (isValidType(`object`)) {
                dest.writeString(TYPE_NATIVE)
                dest.writeValue(`object`)
            } else {
                throw IllegalArgumentException(
                    "Could not encode this object into Parcel. "
                            + `object`.javaClass.toString()
                )
            }
        } catch (e: Exception) {
            throw IllegalArgumentException(
                "Could not encode this object into Parcel. "
                        + `object`!!.javaClass.toString()
            )
        }
    }

    protected open fun encodeParseObject(`object`: ParseObject, dest: Parcel) {
        dest.writeString(TYPE_OBJECT)
        `object`.writeToParcel(dest, this)
    }

    protected fun encodePointer(className: String?, objectId: String?, dest: Parcel) {
        dest.writeString(TYPE_POINTER)
        dest.writeString(className)
        dest.writeString(objectId)
    }

    companion object {

        const val TYPE_OBJECT = "Object"
        const val TYPE_POINTER = "Pointer"
        const val TYPE_DATE = "Date"
        const val TYPE_BYTES = "Bytes"
        const val TYPE_ACL = "Acl"
        const val TYPE_RELATION = "Relation"
        const val TYPE_MAP = "Map"
        const val TYPE_COLLECTION = "Collection"
        const val TYPE_JSON_NULL = "JsonNull"
        const val TYPE_NULL = "Null"
        const val TYPE_NATIVE = "Native"
        const val TYPE_OP = "Operation"
        const val TYPE_FILE = "File"
        const val TYPE_GEOPOINT = "GeoPoint"
        const val TYPE_POLYGON = "Polygon"

        // This class isn't really a Singleton, but since it has no state, it's more efficient to get the
        // default instance.
        private val INSTANCE = ParseParcelEncoder()
        @JvmStatic
        fun get(): ParseParcelEncoder {
            return INSTANCE
        }

        private fun isValidType(value: Any): Boolean {
            // This encodes to parcel what ParseEncoder does for JSON
            return ParseEncoder.isValidType(value)
        }
    }
}
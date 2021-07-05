/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse

import android.util.Base64
import com.parse.ParseQuery.RelationConstraint
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.*

/**
 * A `ParseEncoder` can be used to transform objects such as [ParseObject]s into JSON
 * data structures.
 *
 * @see com.parse.ParseDecoder
 */
abstract class ParseEncoder {
    fun encode(`object`: Any?): Any {
        try {
            if (`object` is ParseObject) {
                return encodeRelatedObject(`object`)
            }

            // TODO(grantland): Remove once we disallow mutable nested queries t6941155
            if (`object` is ParseQuery.State.Builder<*>) {
                return encode(`object`.build())
            }
            if (`object` is ParseQuery.State<*>) {
                return `object`.toJSON(this)
            }
            if (`object` is Date) {
                return encodeDate(`object` as Date?)
            }
            if (`object` is ByteArray) {
                val json = JSONObject()
                json.put("__type", "Bytes")
                json.put("base64", Base64.encodeToString(`object` as ByteArray?, Base64.NO_WRAP))
                return json
            }
            if (`object` is ParseFile) {
                return `object`.encode()
            }
            if (`object` is ParseGeoPoint) {
                val json = JSONObject()
                json.put("__type", "GeoPoint")
                json.put("latitude", `object`.latitude)
                json.put("longitude", `object`.longitude)
                return json
            }
            if (`object` is ParsePolygon) {
                val json = JSONObject()
                json.put("__type", "Polygon")
                json.put("coordinates", `object`.coordinatesToJSONArray())
                return json
            }
            if (`object` is ParseACL) {
                return `object`.toJSONObject(this)
            }
            if (`object` is Map<*, *>) {
                val map = `object` as Map<String, Any?>
                val json = JSONObject()
                for ((key, value) in map) {
                    json.put(key, encode(value))
                }
                return json
            }
            if (`object` is Collection<*>) {
                val array = JSONArray()
                for (item in `object`) {
                    array.put(encode(item))
                }
                return array
            }
            if (`object` is ParseRelation<*>) {
                return `object`.encodeToJSON(this)
            }
            if (`object` is ParseFieldOperation) {
                return `object`.encode(this)
            }
            if (`object` is RelationConstraint) {
                return `object`.encode(this)
            }
            if (`object` == null) {
                return JSONObject.NULL
            }
        } catch (e: JSONException) {
            throw RuntimeException(e)
        }

        // String, Number, Boolean,
        if (isValidType(`object`)) {
            return `object`
        }
        throw IllegalArgumentException(
            "invalid type for ParseObject: "
                    + `object`.javaClass.toString()
        )
    }

    abstract fun encodeRelatedObject(`object`: ParseObject): JSONObject

    protected fun encodeDate(date: Date?): JSONObject {
        val `object` = JSONObject()
        val iso = ParseDateFormat.getInstance().format(date)
        try {
            `object`.put("__type", "Date")
            `object`.put("iso", iso)
        } catch (e: JSONException) {
            // This should not happen
            throw RuntimeException(e)
        }
        return `object`
    }

    companion object {
        /* package */
        @JvmStatic
        fun isValidType(value: Any): Boolean {
            return (value is String
                    || value is Number
                    || value is Boolean
                    || value is Date
                    || value is List<*>
                    || value is Map<*, *>
                    || value is ByteArray
                    || value === JSONObject.NULL || value is ParseObject
                    || value is ParseACL
                    || value is ParseFile
                    || value is ParseGeoPoint
                    || value is ParsePolygon
                    || value is ParseRelation<*>)
        }
    }
}
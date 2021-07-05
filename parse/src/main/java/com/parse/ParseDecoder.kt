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
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.*

/**
 * A `ParseDecoder` can be used to transform JSON data structures into actual objects, such as
 * [ParseObject]s.
 *
 * @see com.parse.ParseEncoder
 */
open class ParseDecoder protected constructor() {
    /* package */
    fun convertJSONArrayToList(array: JSONArray): List<Any?> {
        val list: MutableList<Any?> = ArrayList()
        for (i in 0 until array.length()) {
            list.add(decode(array.opt(i)))
        }
        return list
    }

    /* package */
    fun convertJSONObjectToMap(`object`: JSONObject): Map<String, Any?> {
        val outputMap: MutableMap<String, Any?> = HashMap()
        val it = `object`.keys()
        while (it.hasNext()) {
            val key = it.next()
            val value = `object`.opt(key)
            outputMap[key] = decode(value)
        }
        return outputMap
    }

    /**
     * Gets the `ParseObject` another object points to. By default a new
     * object will be created.
     */
    protected open fun decodePointer(className: String?, objectId: String?): ParseObject? {
        return ParseObject.createWithoutData(className, objectId)
    }

    open fun decode(`object`: Any): Any? {
        if (`object` is JSONArray) {
            return convertJSONArrayToList(`object`)
        }
        if (`object` === JSONObject.NULL) {
            return null
        }
        if (`object` !is JSONObject) {
            return `object`
        }
        val opString = `object`.optString("__op", null)
        if (opString != null) {
            return try {
                ParseFieldOperations.decode(`object`, this)
            } catch (e: JSONException) {
                throw RuntimeException(e)
            }
        }
        val typeString = `object`.optString("__type", null)
            ?: return convertJSONObjectToMap(`object`)
        if (typeString == "Date") {
            val iso = `object`.optString("iso")
            return ParseDateFormat.getInstance().parse(iso)
        }
        if (typeString == "Bytes") {
            val base64 = `object`.optString("base64")
            return Base64.decode(base64, Base64.NO_WRAP)
        }
        if (typeString == "Pointer") {
            return decodePointer(
                `object`.optString("className"),
                `object`.optString("objectId")
            )
        }
        if (typeString == "File") {
            return ParseFile(`object`, this)
        }
        if (typeString == "GeoPoint") {
            val latitude: Double
            val longitude: Double
            try {
                latitude = `object`.getDouble("latitude")
                longitude = `object`.getDouble("longitude")
            } catch (e: JSONException) {
                throw RuntimeException(e)
            }
            return ParseGeoPoint(latitude, longitude)
        }
        if (typeString == "Polygon") {
            val coordinates: MutableList<ParseGeoPoint> = ArrayList()
            try {
                val array = `object`.getJSONArray("coordinates")
                for (i in 0 until array.length()) {
                    val point = array.getJSONArray(i)
                    coordinates.add(ParseGeoPoint(point.getDouble(0), point.getDouble(1)))
                }
            } catch (e: JSONException) {
                throw RuntimeException(e)
            }
            return ParsePolygon(coordinates)
        }
        if (typeString == "Object") {
            return ParseObject.fromJSON(`object`, null, this)
        }
        if (typeString == "Relation") {
            return ParseRelation<ParseObject>(`object`, this)
        }
        if (typeString == "OfflineObject") {
            throw RuntimeException("An unexpected offline pointer was encountered.")
        }
        return null
    }

    companion object {
        // This class isn't really a Singleton, but since it has no state, it's more efficient to get the
        // default instance.
        private val INSTANCE = ParseDecoder()
        @JvmStatic
        fun get(): ParseDecoder {
            return INSTANCE
        }
    }
}
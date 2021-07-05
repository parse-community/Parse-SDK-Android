/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse

import org.json.JSONException
import org.json.JSONObject
import java.util.*

/**
 * Handles encoding/decoding ParseObjects to/from /2 format JSON. /2 format json is only used for
 * persisting current ParseObject(currentInstallation, currentUser) to disk when LDS is not enabled.
 */
internal open class ParseObjectCurrentCoder  /* package */
    : ParseObjectCoder() {
    /**
     * Converts a `ParseObject` to /2/ JSON representation suitable for saving to disk.
     *
     *
     * <pre>
     * {
     * data: {
     * // data fields, including objectId, createdAt, updatedAt
     * },
     * classname: class name for the object,
     * operations: { } // operations per field
     * }
    </pre> *
     *
     *
     * All keys are included, regardless of whether they are dirty.
     *
     * @see .decode
     */
    override fun <T : ParseObject.State> encode(
        state: T, operations: ParseOperationSet?, encoder: ParseEncoder
    ): JSONObject? {
        // Public data goes in dataJSON; special fields go in objectJSON.
        val objectJSON = JSONObject()
        val dataJSON = JSONObject()
        try {
            // Serialize the data
            for (key in state.keySet()) {
                val `object` = state[key]
                dataJSON.put(key, encoder.encode(`object`))

                // TODO(grantland): Use cached value from hashedObjects, but only if we're not dirty.
            }
            if (state.createdAt() > 0) {
                dataJSON.put(
                    KEY_CREATED_AT,
                    ParseDateFormat.getInstance().format(Date(state.createdAt()))
                )
            }
            if (state.updatedAt() > 0) {
                dataJSON.put(
                    KEY_UPDATED_AT,
                    ParseDateFormat.getInstance().format(Date(state.updatedAt()))
                )
            }
            if (state.objectId() != null) {
                dataJSON.put(KEY_OBJECT_ID, state.objectId())
            }
            objectJSON.put(KEY_DATA, dataJSON)
            objectJSON.put(KEY_CLASS_NAME, state.className())
        } catch (e: JSONException) {
            throw RuntimeException("could not serialize object to JSON")
        }
        return objectJSON
    }

    /**
     * Decodes from /2/ JSON.
     *
     *
     * This is only used to read ParseObjects stored on disk in JSON.
     *
     * @see .encode
     */
    override fun <T : ParseObject.State.Init<*>> decode(
        builder: T, json: JSONObject, decoder: ParseDecoder
    ): T {
        return try {
            // The handlers for id, created_at, updated_at, and pointers are for
            // backward compatibility with old serialized users.
            if (json.has(KEY_OLD_OBJECT_ID)) {
                val newObjectId = json.getString(KEY_OLD_OBJECT_ID)
                builder.objectId(newObjectId)
            }
            if (json.has(KEY_OLD_CREATED_AT)) {
                val createdAtString = json.getString(KEY_OLD_CREATED_AT)
                builder.createdAt(
                    ParseImpreciseDateFormat.getInstance().parse(createdAtString)
                )
            }
            if (json.has(KEY_OLD_UPDATED_AT)) {
                val updatedAtString = json.getString(KEY_OLD_UPDATED_AT)
                builder.updatedAt(
                    ParseImpreciseDateFormat.getInstance().parse(updatedAtString)
                )
            }
            if (json.has(KEY_OLD_POINTERS)) {
                val newPointers = json.getJSONObject(KEY_OLD_POINTERS)
                val keys: Iterator<*> = newPointers.keys()
                while (keys.hasNext()) {
                    val key = keys.next() as String
                    val pointerArray = newPointers.getJSONArray(key)
                    builder.put(
                        key, ParseObject.createWithoutData(
                            pointerArray.optString(0),
                            pointerArray.optString(1)
                        )
                    )
                }
            }
            val data = json.optJSONObject(KEY_DATA)
            if (data != null) {
                val keys: Iterator<*> = data.keys()
                while (keys.hasNext()) {
                    val key = keys.next() as String
                    when (key) {
                        KEY_OBJECT_ID -> {
                            val newObjectId = data.getString(key)
                            builder.objectId(newObjectId)
                        }
                        KEY_CREATED_AT -> builder.createdAt(
                            ParseDateFormat.getInstance().parse(data.getString(key))
                        )
                        KEY_UPDATED_AT -> builder.updatedAt(
                            ParseDateFormat.getInstance().parse(data.getString(key))
                        )
                        KEY_ACL -> {
                            val acl =
                                ParseACL.createACLFromJSONObject(data.getJSONObject(key), decoder)
                            builder.put(KEY_ACL, acl)
                        }
                        else -> {
                            val value = data[key]
                            val decodedObject = decoder.decode(value)
                            builder.put(key, decodedObject)
                        }
                    }
                }
            }
            builder
        } catch (e: JSONException) {
            throw RuntimeException(e)
        }
    }

    companion object {
        /*
    /2 format JSON Keys
    */
        private const val KEY_OBJECT_ID = "objectId"
        private const val KEY_CLASS_NAME = "classname"
        private const val KEY_CREATED_AT = "createdAt"
        private const val KEY_UPDATED_AT = "updatedAt"
        private const val KEY_ACL = "ACL"
        private const val KEY_DATA = "data"

        /*
    Old serialized JSON keys
     */
        private const val KEY_OLD_OBJECT_ID = "id"
        private const val KEY_OLD_CREATED_AT = "created_at"
        private const val KEY_OLD_UPDATED_AT = "updated_at"
        private const val KEY_OLD_POINTERS = "pointers"
        private val INSTANCE = ParseObjectCurrentCoder()
        @JvmStatic
        fun get(): ParseObjectCurrentCoder {
            return INSTANCE
        }
    }
}
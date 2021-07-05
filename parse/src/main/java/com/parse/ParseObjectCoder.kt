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

/**
 * Handles encoding/decoding ParseObjects to/from REST JSON.
 */
internal open class ParseObjectCoder  /* package */ {
    /**
     * Converts a `ParseObject.State` to REST JSON for saving.
     *
     *
     * Only dirty keys from `operations` are represented in the data. Non-dirty keys such as
     * `updatedAt`, `createdAt`, etc. are not included.
     *
     * @param state      [ParseObject.State] of the type of [ParseObject] that will be returned.
     * Properties are completely ignored.
     * @param operations Dirty operations that are to be saved.
     * @param encoder    Encoder instance that will be used to encode the request.
     * @return A REST formatted [JSONObject] that will be used for saving.
     */
    open fun <T : ParseObject.State> encode(
        state: T, operations: ParseOperationSet?, encoder: ParseEncoder
    ): JSONObject? {
        val objectJSON = JSONObject()
        try {
            // Serialize the data
                operations?.let {
                    for (key in it.keys) {
                        val operation = it[key]
                        objectJSON.put(key, encoder.encode(operation))

                        // TODO(grantland): Use cached value from hashedObjects if it's a set operation.
                    }
                }

            if (state.objectId() != null) {
                objectJSON.put(KEY_OBJECT_ID, state.objectId())
            }
        } catch (e: JSONException) {
            throw RuntimeException("could not serialize object to JSON")
        }
        return objectJSON
    }

    /**
     * Converts REST JSON response to [ParseObject.State.Init].
     *
     *
     * This returns Builder instead of a State since we'll probably want to set some additional
     * properties on it after decoding such as [ParseObject.State.Init.isComplete], etc.
     *
     * @param builder A [ParseObject.State.Init] instance that will have the server JSON applied
     * (mutated) to it. This will generally be a instance created by clearing a mutable
     * copy of a [ParseObject.State] to ensure it's an instance of the correct
     * subclass: `state.newBuilder().clear()`
     * @param json    JSON response in REST format from the server.
     * @param decoder Decoder instance that will be used to decode the server response.
     * @return The same Builder instance passed in after the JSON is applied.
     */
    open fun <T : ParseObject.State.Init<*>> decode(
        builder: T, json: JSONObject, decoder: ParseDecoder
    ): T {
        return try {
            val keys: Iterator<*> = json.keys()
            while (keys.hasNext()) {
                val key = keys.next() as String
                /*
                __type:       Returned by queries and cloud functions to designate body is a ParseObject
                __className:  Used by fromJSON, should be stripped out by the time it gets here...
                */
                if (key == "__type" || key == KEY_CLASS_NAME) {
                    continue
                }
                if (key == KEY_OBJECT_ID) {
                    val newObjectId = json.getString(key)
                    builder.objectId(newObjectId)
                    continue
                }
                if (key == KEY_CREATED_AT) {
                    builder.createdAt(ParseDateFormat.getInstance().parse(json.getString(key)))
                    continue
                }
                if (key == KEY_UPDATED_AT) {
                    builder.updatedAt(ParseDateFormat.getInstance().parse(json.getString(key)))
                    continue
                }
                if (key == KEY_ACL) {
                    val acl = ParseACL.createACLFromJSONObject(json.getJSONObject(key), decoder)
                    builder.put(KEY_ACL, acl)
                    continue
                }
                val value = json[key]
                val decodedObject = decoder.decode(value)
                builder.put(key, decodedObject)
            }
            builder
        } catch (e: JSONException) {
            throw RuntimeException(e)
        }
    }

    companion object {
        private const val KEY_OBJECT_ID = "objectId"
        private const val KEY_CLASS_NAME = "className"
        private const val KEY_ACL = "ACL"
        private const val KEY_CREATED_AT = "createdAt"
        private const val KEY_UPDATED_AT = "updatedAt"
        private val INSTANCE = ParseObjectCoder()
        @JvmStatic
        fun get(): ParseObjectCoder {
            return INSTANCE
        }
    }
}
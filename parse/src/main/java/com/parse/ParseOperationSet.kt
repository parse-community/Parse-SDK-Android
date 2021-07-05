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
import org.json.JSONException
import org.json.JSONObject
import java.util.*

/**
 * A set of field-level operations that can be performed on an object, corresponding to one command.
 * For example, all of the data for a single call to save() will be packaged here. It is assumed
 * that the ParseObject that owns the operations handles thread-safety.
 */
class ParseOperationSet : HashMap<String, ParseFieldOperation> {
    // A unique id for this operation set.
    val uuid: String

    // Does this set correspond to a call to saveEventually?
    var isSaveEventually = false

    /**
     * Creates a new operation set with a random UUID.
     */
    constructor() : this(UUID.randomUUID().toString())
    constructor(operations: ParseOperationSet) : super(operations) {
        uuid = operations.uuid
        isSaveEventually = operations.isSaveEventually
    }

    /**
     * Creates a new operation set with the given UUID.
     */
    private constructor(uuid: String) {
        this.uuid = uuid
    }

    /**
     * Merges the changes from the given operation set into this one. Most typically, this is what
     * happens when a save fails and changes need to be rolled into the next save.
     */
    fun mergeFrom(other: ParseOperationSet) {
        for (key in other.keys) {
            val operation1 = other[key]
            var operation2 = get(key)
            operation2 = if (operation2 != null) {
                operation2.mergeWithPrevious(operation1)
            } else {
                operation1
            }
            if (operation2 != null) {
                put(key, operation2)
            }
        }
    }

    /**
     * Converts this operation set into its REST format for serializing to LDS.
     */
    @Throws(JSONException::class)
    fun toRest(objectEncoder: ParseEncoder?): JSONObject {
        val operationSetJSON = JSONObject()
        for (key in keys) {
            val op = get(key)
            operationSetJSON.put(key, op!!.encode(objectEncoder))
        }
        operationSetJSON.put(REST_KEY_UUID, uuid)
        if (isSaveEventually) {
            operationSetJSON.put(REST_KEY_IS_SAVE_EVENTUALLY, true)
        }
        return operationSetJSON
    }

    /**
     * Parcels this operation set into a Parcel with the given encoder.
     */
    /* package */
    fun toParcel(dest: Parcel, encoder: ParseParcelEncoder) {
        dest.writeString(uuid)
        dest.writeByte(if (isSaveEventually) 1.toByte() else 0)
        dest.writeInt(size)
        for (key in keys) {
            dest.writeString(key)
            encoder.encode(get(key), dest)
        }
    }

    companion object {
        private const val serialVersionUID = 1L
        private const val REST_KEY_IS_SAVE_EVENTUALLY = "__isSaveEventually"
        private const val REST_KEY_UUID = "__uuid"

        /**
         * The inverse of toRest. Creates a new OperationSet from the given JSON.
         */
        @Throws(JSONException::class)
        fun fromRest(json: JSONObject, decoder: ParseDecoder): ParseOperationSet {
            // Copy the json object to avoid making changes to the old object
            val keysIter = json.keys()
            val keys = arrayOfNulls<String>(json.length())
            var index = 0
            while (keysIter.hasNext()) {
                val key = keysIter.next()
                keys[index++] = key
            }
            val jsonCopy = JSONObject(json, keys)
            val uuid = jsonCopy.remove(REST_KEY_UUID) as String
            val operationSet = ParseOperationSet(uuid)
            val isSaveEventually = jsonCopy.optBoolean(REST_KEY_IS_SAVE_EVENTUALLY)
            jsonCopy.remove(REST_KEY_IS_SAVE_EVENTUALLY)
            operationSet.isSaveEventually = isSaveEventually
            val opKeys: Iterator<*> = jsonCopy.keys()
            while (opKeys.hasNext()) {
                val opKey = opKeys.next() as String
                var value = decoder.decode(jsonCopy[opKey])
                if (opKey == "ACL") {
                    value = ParseACL.createACLFromJSONObject(jsonCopy.getJSONObject(opKey), decoder)
                }
                val fieldOp: ParseFieldOperation = if (value is ParseFieldOperation) {
                    value
                } else {
                    ParseSetOperation(value)
                }
                operationSet[opKey] = fieldOp
            }
            return operationSet
        }

        /* package */
        fun fromParcel(source: Parcel, decoder: ParseParcelDecoder): ParseOperationSet {
            val set = ParseOperationSet(source.readString()!!)
            set.isSaveEventually = source.readByte().toInt() == 1
            val size = source.readInt()
            for (i in 0 until size) {
                val key = source.readString()!!
                val op = decoder.decode(source) as ParseFieldOperation
                set[key] = op
            }
            return set
        }
    }
}
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
 * Encodes [ParseObject]s as pointers. If the object does not have an objectId, uses a
 * local id.
 */
open class PointerOrLocalIdEncoder : ParseEncoder() {
    override fun encodeRelatedObject(`object`: ParseObject): JSONObject {
        val json = JSONObject()
        try {
            if (`object`.objectId != null) {
                json.put("__type", "Pointer")
                json.put("className", `object`.className)
                json.put("objectId", `object`.objectId)
            } else {
                json.put("__type", "Pointer")
                json.put("className", `object`.className)
                json.put("localId", `object`.getOrCreateLocalId())
            }
        } catch (e: JSONException) {
            // This should not happen
            throw RuntimeException(e)
        }
        return json
    }

    companion object {
        // This class isn't really a Singleton, but since it has no state, it's more efficient to get the
        // default instance.
        private val INSTANCE = PointerOrLocalIdEncoder()
        @JvmStatic
        fun get(): PointerOrLocalIdEncoder {
            return INSTANCE
        }
    }
}
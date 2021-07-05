/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse

import org.json.JSONObject

/**
 * Encodes [ParseObject]s as pointers. If the object does not have an objectId, throws an
 * exception.
 */
class PointerEncoder : PointerOrLocalIdEncoder() {
    override fun encodeRelatedObject(`object`: ParseObject): JSONObject {
        // Ensure the ParseObject has an id so it can be encoded as a pointer.
        checkNotNull(`object`.objectId) {
            // object that hasn't been saved.
            "unable to encode an association with an unsaved ParseObject"
        }
        return super.encodeRelatedObject(`object`)
    }

    companion object {
        // This class isn't really a Singleton, but since it has no state, it's more efficient to get the
        // default instance.
        private val INSTANCE = PointerEncoder()
        @JvmStatic
        fun get(): PointerEncoder {
            return INSTANCE
        }
    }
}
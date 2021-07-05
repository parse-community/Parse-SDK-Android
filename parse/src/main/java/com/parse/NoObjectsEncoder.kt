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
 * Throws an exception if someone attempts to encode a `ParseObject`.
 */
internal class NoObjectsEncoder : ParseEncoder() {
    override fun encodeRelatedObject(`object`: ParseObject): JSONObject {
        throw IllegalArgumentException("ParseObjects not allowed here")
    }

    companion object {
        // This class isn't really a Singleton, but since it has no state, it's more efficient to get the
        // default instance.
        private val INSTANCE = NoObjectsEncoder()
        @JvmStatic
        fun get(): NoObjectsEncoder {
            return INSTANCE
        }
    }
}
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
 * Handles encoding/decoding ParseUser to/from /2 format JSON. /2 format json is only used for
 * persisting current ParseUser and ParseInstallation to disk when LDS is not enabled.
 */
internal class ParseUserCurrentCoder  /* package */
    : ParseObjectCurrentCoder() {
    /**
     * Converts a ParseUser state to /2/ JSON representation suitable for saving to disk.
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
     * We also add sessionToken and authData to the json.
     *
     * @see .decode
     */
    override fun <T : ParseObject.State> encode(
        state: T, operations: ParseOperationSet?, encoder: ParseEncoder
    ): JSONObject? {

        // FYI we'll be double writing sessionToken and authData for now...
        // This is important. super.encode() has no notion of sessionToken and authData, so it treats them
        // like objects (simply passed to the encoder). This means that a null sessionToken will become
        // JSONObject.NULL. This must be accounted in #decode().
        val objectJSON = super.encode(state, operations, encoder)
        val sessionToken = (state as ParseUser.State).sessionToken()
        if (sessionToken != null) {
            try {
                objectJSON!!.put(KEY_SESSION_TOKEN, sessionToken)
            } catch (e: JSONException) {
                throw RuntimeException("could not encode value for key: session_token")
            }
        }
        val authData = (state as ParseUser.State).authData()
        if (authData.isNotEmpty()) {
            try {
                objectJSON!!.put(KEY_AUTH_DATA, encoder.encode(authData))
            } catch (e: JSONException) {
                throw RuntimeException("could not attach key: auth_data")
            }
        }
        return objectJSON
    }

    /**
     * Merges from JSON in /2/ format.
     *
     *
     * This is only used to read ParseUser state stored on disk in JSON.
     * Since in encode we add sessionToken and authData to the json, we need remove them from json
     * to generate state.
     *
     * @see .encode
     */
    override fun <T : ParseObject.State.Init<*>> decode(
        builder: T, json: JSONObject, decoder: ParseDecoder
    ): T {
        val userBuilder = super.decode(builder, json, decoder) as ParseUser.State.Builder

        // super.decode will read its own values and add them to the builder using put().
        // This means the state for session token and auth data might be illegal, returning
        // unexpected types. For instance if sessionToken was null, now it's JSONObject.NULL.
        // We must overwrite these possibly wrong values.
        val newSessionToken = json.optString(KEY_SESSION_TOKEN, null)
        userBuilder.sessionToken(newSessionToken)
        val newAuthData = json.optJSONObject(KEY_AUTH_DATA)
        if (newAuthData == null) {
            userBuilder.authData(null)
        } else {
            try {
                val i: Iterator<*> = newAuthData.keys()
                while (i.hasNext()) {
                    val key = i.next() as String
                    if (!newAuthData.isNull(key)) {
                        userBuilder.putAuthData(
                            key,
                            ParseDecoder.get()
                                .decode(newAuthData.getJSONObject(key)) as Map<String, String>
                        )
                    }
                }
            } catch (e: JSONException) {
                throw RuntimeException(e)
            }
        }
        return userBuilder as T
    }

    companion object {
        private const val KEY_AUTH_DATA = "auth_data"
        private const val KEY_SESSION_TOKEN = "session_token"
        private val INSTANCE = ParseUserCurrentCoder()
        @JvmStatic
        fun get(): ParseUserCurrentCoder {
            return INSTANCE
        }
    }
}
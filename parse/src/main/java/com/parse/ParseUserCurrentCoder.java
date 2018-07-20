/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.Map;

import static com.parse.ParseUser.State;

/**
 * Handles encoding/decoding ParseUser to/from /2 format JSON. /2 format json is only used for
 * persisting current ParseUser and ParseInstallation to disk when LDS is not enabled.
 */
class ParseUserCurrentCoder extends ParseObjectCurrentCoder {

    private static final String KEY_AUTH_DATA = "auth_data";
    private static final String KEY_SESSION_TOKEN = "session_token";

    private static final ParseUserCurrentCoder INSTANCE = new ParseUserCurrentCoder();

    /* package */ ParseUserCurrentCoder() {
        // do nothing
    }

    public static ParseUserCurrentCoder get() {
        return INSTANCE;
    }

    /**
     * Converts a ParseUser state to /2/ JSON representation suitable for saving to disk.
     * <p>
     * <pre>
     * {
     *   data: {
     *     // data fields, including objectId, createdAt, updatedAt
     *   },
     *   classname: class name for the object,
     *   operations: { } // operations per field
     * }
     * </pre>
     * <p>
     * All keys are included, regardless of whether they are dirty.
     * We also add sessionToken and authData to the json.
     *
     * @see #decode(ParseObject.State.Init, JSONObject, ParseDecoder)
     */
    @Override
    public <T extends ParseObject.State> JSONObject encode(
            T state, ParseOperationSet operations, ParseEncoder encoder) {

        // FYI we'll be double writing sessionToken and authData for now...
        // This is important. super.encode() has no notion of sessionToken and authData, so it treats them
        // like objects (simply passed to the encoder). This means that a null sessionToken will become
        // JSONObject.NULL. This must be accounted in #decode().
        JSONObject objectJSON = super.encode(state, operations, encoder);

        String sessionToken = ((State) state).sessionToken();
        if (sessionToken != null) {
            try {
                objectJSON.put(KEY_SESSION_TOKEN, sessionToken);
            } catch (JSONException e) {
                throw new RuntimeException("could not encode value for key: session_token");
            }
        }

        Map<String, Map<String, String>> authData = ((State) state).authData();
        if (authData.size() > 0) {
            try {
                objectJSON.put(KEY_AUTH_DATA, encoder.encode(authData));
            } catch (JSONException e) {
                throw new RuntimeException("could not attach key: auth_data");
            }
        }

        return objectJSON;
    }

    /**
     * Merges from JSON in /2/ format.
     * <p>
     * This is only used to read ParseUser state stored on disk in JSON.
     * Since in encode we add sessionToken and authData to the json, we need remove them from json
     * to generate state.
     *
     * @see #encode(ParseObject.State, ParseOperationSet, ParseEncoder)
     */
    @Override
    public <T extends ParseObject.State.Init<?>> T decode(
            T builder, JSONObject json, ParseDecoder decoder) {
        ParseUser.State.Builder userBuilder = (State.Builder) super.decode(builder, json, decoder);

        // super.decode will read its own values and add them to the builder using put().
        // This means the state for session token and auth data might be illegal, returning
        // unexpected types. For instance if sessionToken was null, now it's JSONObject.NULL.
        // We must overwrite these possibly wrong values.
        String newSessionToken = json.optString(KEY_SESSION_TOKEN, null);
        userBuilder.sessionToken(newSessionToken);

        JSONObject newAuthData = json.optJSONObject(KEY_AUTH_DATA);
        if (newAuthData == null) {
            userBuilder.authData(null);
        } else {
            try {
                @SuppressWarnings("rawtypes")
                Iterator i = newAuthData.keys();
                while (i.hasNext()) {
                    String key = (String) i.next();
                    if (!newAuthData.isNull(key)) {
                        userBuilder.putAuthData(key,
                                (Map<String, String>) ParseDecoder.get().decode(newAuthData.getJSONObject(key)));
                    }
                }
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }

        return (T) userBuilder;
    }
}

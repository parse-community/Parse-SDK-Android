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

/**
 * Handles encoding/decoding ParseObjects to/from REST JSON.
 */
class ParseObjectCoder {

    private static final String KEY_OBJECT_ID = "objectId";
    private static final String KEY_CLASS_NAME = "className";
    private static final String KEY_ACL = "ACL";
    private static final String KEY_CREATED_AT = "createdAt";
    private static final String KEY_UPDATED_AT = "updatedAt";

    private static final ParseObjectCoder INSTANCE = new ParseObjectCoder();

    /* package */ ParseObjectCoder() {
        // do nothing
    }

    public static ParseObjectCoder get() {
        return INSTANCE;
    }

    /**
     * Converts a {@code ParseObject.State} to REST JSON for saving.
     * <p>
     * Only dirty keys from {@code operations} are represented in the data. Non-dirty keys such as
     * {@code updatedAt}, {@code createdAt}, etc. are not included.
     *
     * @param state      {@link ParseObject.State} of the type of {@link ParseObject} that will be returned.
     *                   Properties are completely ignored.
     * @param operations Dirty operations that are to be saved.
     * @param encoder    Encoder instance that will be used to encode the request.
     * @return A REST formatted {@link JSONObject} that will be used for saving.
     */
    public <T extends ParseObject.State> JSONObject encode(
            T state, ParseOperationSet operations, ParseEncoder encoder) {
        JSONObject objectJSON = new JSONObject();

        try {
            // Serialize the data
            for (String key : operations.keySet()) {
                ParseFieldOperation operation = operations.get(key);
                objectJSON.put(key, encoder.encode(operation));

                // TODO(grantland): Use cached value from hashedObjects if it's a set operation.
            }

            if (state.objectId() != null) {
                objectJSON.put(KEY_OBJECT_ID, state.objectId());
            }
        } catch (JSONException e) {
            throw new RuntimeException("could not serialize object to JSON");
        }

        return objectJSON;
    }

    /**
     * Converts REST JSON response to {@link ParseObject.State.Init}.
     * <p>
     * This returns Builder instead of a State since we'll probably want to set some additional
     * properties on it after decoding such as {@link ParseObject.State.Init#isComplete()}, etc.
     *
     * @param builder A {@link ParseObject.State.Init} instance that will have the server JSON applied
     *                (mutated) to it. This will generally be a instance created by clearing a mutable
     *                copy of a {@link ParseObject.State} to ensure it's an instance of the correct
     *                subclass: {@code state.newBuilder().clear()}
     * @param json    JSON response in REST format from the server.
     * @param decoder Decoder instance that will be used to decode the server response.
     * @return The same Builder instance passed in after the JSON is applied.
     */
    public <T extends ParseObject.State.Init<?>> T decode(
            T builder, JSONObject json, ParseDecoder decoder) {
        try {
            Iterator<?> keys = json.keys();
            while (keys.hasNext()) {
                String key = (String) keys.next();
        /*
        __type:       Returned by queries and cloud functions to designate body is a ParseObject
        __className:  Used by fromJSON, should be stripped out by the time it gets here...
         */
                if (key.equals("__type") || key.equals(KEY_CLASS_NAME)) {
                    continue;
                }
                if (key.equals(KEY_OBJECT_ID)) {
                    String newObjectId = json.getString(key);
                    builder.objectId(newObjectId);
                    continue;
                }
                if (key.equals(KEY_CREATED_AT)) {
                    builder.createdAt(ParseDateFormat.getInstance().parse(json.getString(key)));
                    continue;
                }
                if (key.equals(KEY_UPDATED_AT)) {
                    builder.updatedAt(ParseDateFormat.getInstance().parse(json.getString(key)));
                    continue;
                }
                if (key.equals(KEY_ACL)) {
                    ParseACL acl = ParseACL.createACLFromJSONObject(json.getJSONObject(key), decoder);
                    builder.put(KEY_ACL, acl);
                    continue;
                }

                Object value = json.get(key);
                Object decodedObject = decoder.decode(value);
                builder.put(key, decodedObject);
            }

            return builder;
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }
}

/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Date;
import java.util.Iterator;

/**
 * Handles encoding/decoding ParseObjects to/from /2 format JSON. /2 format json is only used for
 * persisting current ParseObject(currentInstallation, currentUser) to disk when LDS is not enabled.
 */
class ParseObjectCurrentCoder extends ParseObjectCoder {

    /*
    /2 format JSON Keys
    */
    private static final String KEY_OBJECT_ID = "objectId";
    private static final String KEY_CLASS_NAME = "classname";
    private static final String KEY_CREATED_AT = "createdAt";
    private static final String KEY_UPDATED_AT = "updatedAt";
    private static final String KEY_ACL = "ACL";
    private static final String KEY_DATA = "data";

    /*
    Old serialized JSON keys
     */
    private static final String KEY_OLD_OBJECT_ID = "id";
    private static final String KEY_OLD_CREATED_AT = "created_at";
    private static final String KEY_OLD_UPDATED_AT = "updated_at";
    private static final String KEY_OLD_POINTERS = "pointers";

    private static final ParseObjectCurrentCoder INSTANCE =
            new ParseObjectCurrentCoder();

    /* package */ ParseObjectCurrentCoder() {
        // do nothing
    }

    public static ParseObjectCurrentCoder get() {
        return INSTANCE;
    }

    /**
     * Converts a {@code ParseObject} to /2/ JSON representation suitable for saving to disk.
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
     *
     * @see #decode(ParseObject.State.Init, JSONObject, ParseDecoder)
     */
    @Override
    public <T extends ParseObject.State> JSONObject encode(
            T state, ParseOperationSet operations, ParseEncoder encoder) {
        if (operations != null) {
            throw new IllegalArgumentException("Parameter ParseOperationSet is not null");
        }

        // Public data goes in dataJSON; special fields go in objectJSON.
        JSONObject objectJSON = new JSONObject();
        JSONObject dataJSON = new JSONObject();

        try {
            // Serialize the data
            for (String key : state.keySet()) {
                Object object = state.get(key);
                dataJSON.put(key, encoder.encode(object));

                // TODO(grantland): Use cached value from hashedObjects, but only if we're not dirty.
            }

            if (state.createdAt() > 0) {
                dataJSON.put(KEY_CREATED_AT,
                        ParseDateFormat.getInstance().format(new Date(state.createdAt())));
            }
            if (state.updatedAt() > 0) {
                dataJSON.put(KEY_UPDATED_AT,
                        ParseDateFormat.getInstance().format(new Date(state.updatedAt())));
            }
            if (state.objectId() != null) {
                dataJSON.put(KEY_OBJECT_ID, state.objectId());
            }

            objectJSON.put(KEY_DATA, dataJSON);
            objectJSON.put(KEY_CLASS_NAME, state.className());
        } catch (JSONException e) {
            throw new RuntimeException("could not serialize object to JSON");
        }

        return objectJSON;
    }

    /**
     * Decodes from /2/ JSON.
     * <p>
     * This is only used to read ParseObjects stored on disk in JSON.
     *
     * @see #encode(ParseObject.State, ParseOperationSet, ParseEncoder)
     */
    @Override
    public <T extends ParseObject.State.Init<?>> T decode(
            T builder, JSONObject json, ParseDecoder decoder) {
        try {
            // The handlers for id, created_at, updated_at, and pointers are for
            // backward compatibility with old serialized users.
            if (json.has(KEY_OLD_OBJECT_ID)) {
                String newObjectId = json.getString(KEY_OLD_OBJECT_ID);
                builder.objectId(newObjectId);
            }
            if (json.has(KEY_OLD_CREATED_AT)) {
                String createdAtString =
                        json.getString(KEY_OLD_CREATED_AT);
                if (createdAtString != null) {
                    builder.createdAt(ParseImpreciseDateFormat.getInstance().parse(createdAtString));
                }
            }
            if (json.has(KEY_OLD_UPDATED_AT)) {
                String updatedAtString =
                        json.getString(KEY_OLD_UPDATED_AT);
                if (updatedAtString != null) {
                    builder.updatedAt(ParseImpreciseDateFormat.getInstance().parse(updatedAtString));
                }
            }
            if (json.has(KEY_OLD_POINTERS)) {
                JSONObject newPointers =
                        json.getJSONObject(KEY_OLD_POINTERS);
                Iterator<?> keys = newPointers.keys();
                while (keys.hasNext()) {
                    String key = (String) keys.next();
                    JSONArray pointerArray = newPointers.getJSONArray(key);
                    builder.put(key, ParseObject.createWithoutData(pointerArray.optString(0),
                            pointerArray.optString(1)));
                }
            }

            JSONObject data = json.optJSONObject(KEY_DATA);
            if (data != null) {
                Iterator<?> keys = data.keys();
                while (keys.hasNext()) {
                    String key = (String) keys.next();
                    switch (key) {
                        case KEY_OBJECT_ID:
                            String newObjectId = data.getString(key);
                            builder.objectId(newObjectId);
                            break;
                        case KEY_CREATED_AT:
                            builder.createdAt(ParseDateFormat.getInstance().parse(data.getString(key)));
                            break;
                        case KEY_UPDATED_AT:
                            builder.updatedAt(ParseDateFormat.getInstance().parse(data.getString(key)));
                            break;
                        case KEY_ACL:
                            ParseACL acl = ParseACL.createACLFromJSONObject(data.getJSONObject(key), decoder);
                            builder.put(KEY_ACL, acl);
                            break;
                        default:
                            Object value = data.get(key);
                            Object decodedObject = decoder.decode(value);
                            builder.put(key, decodedObject);
                    }
                }
            }
            return builder;
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }
}

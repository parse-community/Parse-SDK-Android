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

/**
 * Encodes {@link ParseObject}s as pointers. If the object does not have an objectId, uses a
 * local id.
 */
public class PointerOrLocalIdEncoder extends ParseEncoder {

    // This class isn't really a Singleton, but since it has no state, it's more efficient to get the
    // default instance.
    private static final PointerOrLocalIdEncoder INSTANCE = new PointerOrLocalIdEncoder();

    public static PointerOrLocalIdEncoder get() {
        return INSTANCE;
    }

    @Override
    public JSONObject encodeRelatedObject(ParseObject object) {
        JSONObject json = new JSONObject();
        try {
            if (object.getObjectId() != null) {
                json.put("__type", "Pointer");
                json.put("className", object.getClassName());
                json.put("objectId", object.getObjectId());
            } else {
                json.put("__type", "Pointer");
                json.put("className", object.getClassName());
                json.put("localId", object.getOrCreateLocalId());
            }
        } catch (JSONException e) {
            // This should not happen
            throw new RuntimeException(e);
        }
        return json;
    }
}

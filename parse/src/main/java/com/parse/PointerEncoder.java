/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import org.json.JSONObject;

/**
 * Encodes {@link ParseObject}s as pointers. If the object does not have an objectId, throws an
 * exception.
 */
public class PointerEncoder extends PointerOrLocalIdEncoder {

    // This class isn't really a Singleton, but since it has no state, it's more efficient to get the
    // default instance.
    private static final PointerEncoder INSTANCE = new PointerEncoder();

    public static PointerEncoder get() {
        return INSTANCE;
    }

    @Override
    public JSONObject encodeRelatedObject(ParseObject object) {
        // Ensure the ParseObject has an id so it can be encoded as a pointer.
        if (object.getObjectId() == null) {
            // object that hasn't been saved.
            throw new IllegalStateException("unable to encode an association with an unsaved ParseObject");
        }
        return super.encodeRelatedObject(object);
    }
}

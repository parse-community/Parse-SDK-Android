/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;

/**
 * Static utility methods pertaining to org.json classes.
 */
class ParseJSONUtils {

    /**
     * Creates a copy of {@code copyFrom}, excluding the keys from {@code excludes}.
     */
    public static JSONObject create(JSONObject copyFrom, Collection<String> excludes) {
        JSONObject json = new JSONObject();
        Iterator<String> iterator = copyFrom.keys();
        while (iterator.hasNext()) {
            String name = iterator.next();
            if (excludes.contains(name)) {
                continue;
            }
            try {
                json.put(name, copyFrom.opt(name));
            } catch (JSONException e) {
                // This shouldn't ever happen since it'll only throw if `name` is null
                throw new RuntimeException(e);
            }
        }
        return json;
    }

    /**
     * A helper for nonugly iterating over JSONObject keys.
     */
    public static Iterable<String> keys(JSONObject object) {
        final JSONObject finalObject = object;
        return new Iterable<String>() {
            @NonNull
            @Override
            public Iterator<String> iterator() {
                return finalObject.keys();
            }
        };
    }

    /**
     * A helper for returning the value mapped by a list of keys, ordered by priority.
     */
    public static int getInt(JSONObject object, List<String> keys) throws JSONException {
        for (String key : keys) {
            try {
                return object.getInt(key);
            } catch (JSONException e) {
                // do nothing
            }
        }
        throw new JSONException("No value for " + keys);
    }
}

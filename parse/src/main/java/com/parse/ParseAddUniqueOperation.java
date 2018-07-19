/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import android.os.Parcel;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * An operation that adds a new element to an array field, only if it wasn't already present.
 */
class ParseAddUniqueOperation implements ParseFieldOperation {
    /* package */ final static String OP_NAME = "AddUnique";

    protected final LinkedHashSet<Object> objects = new LinkedHashSet<>();

    public ParseAddUniqueOperation(Collection<?> col) {
        objects.addAll(col);
    }

    @Override
    public JSONObject encode(ParseEncoder objectEncoder) throws JSONException {
        JSONObject output = new JSONObject();
        output.put("__op", OP_NAME);
        output.put("objects", objectEncoder.encode(new ArrayList<>(objects)));
        return output;
    }

    @Override
    public void encode(Parcel dest, ParseParcelEncoder parcelableEncoder) {
        dest.writeString(OP_NAME);
        dest.writeInt(objects.size());
        for (Object object : objects) {
            parcelableEncoder.encode(object, dest);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public ParseFieldOperation mergeWithPrevious(ParseFieldOperation previous) {
        if (previous == null) {
            return this;
        } else if (previous instanceof ParseDeleteOperation) {
            return new ParseSetOperation(objects);
        } else if (previous instanceof ParseSetOperation) {
            Object value = ((ParseSetOperation) previous).getValue();
            if (value instanceof JSONArray || value instanceof List) {
                return new ParseSetOperation(this.apply(value, null));
            } else {
                throw new IllegalArgumentException("You can only add an item to a List or JSONArray.");
            }
        } else if (previous instanceof ParseAddUniqueOperation) {
            List<Object> previousResult =
                    new ArrayList<>(((ParseAddUniqueOperation) previous).objects);
            return new ParseAddUniqueOperation((List<Object>) this.apply(previousResult, null));
        } else {
            throw new IllegalArgumentException("Operation is invalid after previous operation.");
        }
    }

    @Override
    public Object apply(Object oldValue, String key) {
        if (oldValue == null) {
            return new ArrayList<>(objects);
        } else if (oldValue instanceof JSONArray) {
            ArrayList<Object> old = ParseFieldOperations.jsonArrayAsArrayList((JSONArray) oldValue);
            @SuppressWarnings("unchecked")
            ArrayList<Object> newValue = (ArrayList<Object>) this.apply(old, key);
            return new JSONArray(newValue);
        } else if (oldValue instanceof List) {
            ArrayList<Object> result = new ArrayList<>((List<?>) oldValue);

            // Build up a Map of objectIds of the existing ParseObjects in this field.
            HashMap<String, Integer> existingObjectIds = new HashMap<>();
            for (int i = 0; i < result.size(); i++) {
                if (result.get(i) instanceof ParseObject) {
                    existingObjectIds.put(((ParseObject) result.get(i)).getObjectId(), i);
                }
            }

            // Iterate over the objects to add. If it already exists in the field,
            // remove the old one and add the new one. Otherwise, just add normally.
            for (Object obj : objects) {
                if (obj instanceof ParseObject) {
                    String objectId = ((ParseObject) obj).getObjectId();
                    if (objectId != null && existingObjectIds.containsKey(objectId)) {
                        result.set(existingObjectIds.get(objectId), obj);
                    } else if (!result.contains(obj)) {
                        result.add(obj);
                    }
                } else {
                    if (!result.contains(obj)) {
                        result.add(obj);
                    }
                }
            }
            return result;
        } else {
            throw new IllegalArgumentException("Operation is invalid after previous operation.");
        }
    }
}

/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * A {@code ParseEncoder} can be used to transform objects such as {@link ParseObject}s into JSON
 * data structures.
 *
 * @see com.parse.ParseDecoder
 */
public abstract class ParseEncoder {

    /* package */
    static boolean isValidType(Object value) {
        return value instanceof String
                || value instanceof Number
                || value instanceof Boolean
                || value instanceof Date
                || value instanceof List
                || value instanceof Map
                || value instanceof byte[]
                || value == JSONObject.NULL
                || value instanceof ParseObject
                || value instanceof ParseACL
                || value instanceof ParseFile
                || value instanceof ParseGeoPoint
                || value instanceof ParsePolygon
                || value instanceof ParseRelation;
    }

    public Object encode(Object object) {
        try {
            if (object instanceof ParseObject) {
                return encodeRelatedObject((ParseObject) object);
            }

            // TODO(grantland): Remove once we disallow mutable nested queries t6941155
            if (object instanceof ParseQuery.State.Builder<?>) {
                ParseQuery.State.Builder<?> builder = (ParseQuery.State.Builder<?>) object;
                return encode(builder.build());
            }

            if (object instanceof ParseQuery.State<?>) {
                ParseQuery.State<?> state = (ParseQuery.State<?>) object;
                return state.toJSON(this);
            }

            if (object instanceof Date) {
                return encodeDate((Date) object);
            }

            if (object instanceof byte[]) {
                JSONObject json = new JSONObject();
                json.put("__type", "Bytes");
                json.put("base64", Base64.encodeToString((byte[]) object, Base64.NO_WRAP));
                return json;
            }

            if (object instanceof ParseFile) {
                return ((ParseFile) object).encode();
            }

            if (object instanceof ParseGeoPoint) {
                ParseGeoPoint point = (ParseGeoPoint) object;
                JSONObject json = new JSONObject();
                json.put("__type", "GeoPoint");
                json.put("latitude", point.getLatitude());
                json.put("longitude", point.getLongitude());
                return json;
            }

            if (object instanceof ParsePolygon) {
                ParsePolygon polygon = (ParsePolygon) object;
                JSONObject json = new JSONObject();
                json.put("__type", "Polygon");
                json.put("coordinates", polygon.coordinatesToJSONArray());
                return json;
            }

            if (object instanceof ParseACL) {
                ParseACL acl = (ParseACL) object;
                return acl.toJSONObject(this);
            }

            if (object instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) object;
                JSONObject json = new JSONObject();
                for (Map.Entry<String, Object> pair : map.entrySet()) {
                    json.put(pair.getKey(), encode(pair.getValue()));
                }
                return json;
            }

            if (object instanceof Collection) {
                JSONArray array = new JSONArray();
                for (Object item : (Collection<?>) object) {
                    array.put(encode(item));
                }
                return array;
            }

            if (object instanceof ParseRelation) {
                ParseRelation<?> relation = (ParseRelation<?>) object;
                return relation.encodeToJSON(this);
            }

            if (object instanceof ParseFieldOperation) {
                return ((ParseFieldOperation) object).encode(this);
            }

            if (object instanceof ParseQuery.RelationConstraint) {
                return ((ParseQuery.RelationConstraint) object).encode(this);
            }

            if (object == null) {
                return JSONObject.NULL;
            }

        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

        // String, Number, Boolean,
        if (isValidType(object)) {
            return object;
        }

        throw new IllegalArgumentException("invalid type for ParseObject: "
                + object.getClass().toString());
    }

    protected abstract JSONObject encodeRelatedObject(ParseObject object);

    protected JSONObject encodeDate(Date date) {
        JSONObject object = new JSONObject();
        String iso = ParseDateFormat.getInstance().format(date);
        try {
            object.put("__type", "Date");
            object.put("iso", iso);
        } catch (JSONException e) {
            // This should not happen
            throw new RuntimeException(e);
        }
        return object;
    }
}

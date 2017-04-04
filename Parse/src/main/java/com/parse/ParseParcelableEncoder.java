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
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * A {@code ParseParcelableEncoder} can be used to parcel objects such as {@link ParseObjects}
 * into a {@link android.os.Parcel}.
 *
 * @see com.parse.ParseParcelableDecoder
 */

/** package */ class ParseParcelableEncoder {

  // This class isn't really a Singleton, but since it has no state, it's more efficient to get the
  // default instance.
  private static final ParseParcelableEncoder INSTANCE = new ParseParcelableEncoder();
  public static ParseParcelableEncoder get() {
    return INSTANCE;
  }

  // TODO: remove this and user ParseEncoder.isValidType.
  /* package */ static boolean isValidType(Object value) {
    return value instanceof String
        || value instanceof Number
        || value instanceof Boolean
        || value instanceof Date
        || value instanceof List
        || value instanceof Map
        || value instanceof byte[]
        || value == JSONObject.NULL
        || value instanceof ParseObject
        || value instanceof ParseACL;
        // TODO: waiting merge || value instanceof ParseFile
        // TODO: waiting merge || value instanceof ParseGeoPoint
        // TODO: not done yet || value instanceof ParseRelation;
  }

  /* package */ final static String TYPE_OBJECT = "ParseObject";
  /* package */ final static String TYPE_DATE = "Date";
  /* package */ final static String TYPE_BYTES = "Bytes";
  /* package */ final static String TYPE_ACL = "Acl";
  /* package */ final static String TYPE_MAP = "Map";
  /* package */ final static String TYPE_COLLECTION = "Collection";
  /* package */ final static String TYPE_JSON_NULL = "JsonNull";
  /* package */ final static String TYPE_NULL = "Null";
  /* package */ final static String TYPE_NATIVE = "Native";

  public void encode(Object object, Parcel dest) {
    try {
      if (object instanceof ParseObject) {
        dest.writeString(TYPE_OBJECT);
        encodeParseObject((ParseObject) object, dest);

      } else if (object instanceof Date) {
        dest.writeString(TYPE_DATE);
        dest.writeString(ParseDateFormat.getInstance().format((Date) object));

      } else if (object instanceof byte[]) {
        dest.writeString(TYPE_BYTES);
        byte[] bytes = (byte[]) object;
        dest.writeInt(bytes.length);
        dest.writeByteArray(bytes);

      } else if (object instanceof ParseFile) { // TODO
        throw new IllegalArgumentException("Not supported yet");

      } else if (object instanceof ParseGeoPoint) { // TODO
        throw new IllegalArgumentException("Not supported yet");

      } else if (object instanceof ParseACL) {
        dest.writeString(TYPE_ACL);
        dest.writeParcelable((ParseACL) object, 0);

      } else if (object instanceof Map) {
        dest.writeString(TYPE_MAP);
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) object;
        dest.writeInt(map.size());
        for (Map.Entry<String, Object> pair : map.entrySet()) {
          dest.writeString(pair.getKey());
          encode(pair.getValue(), dest);
        }

      } else if (object instanceof Collection) {
        dest.writeString(TYPE_COLLECTION);
        Collection<?> collection = (Collection<?>) object;
        dest.writeInt(collection.size());
        for (Object item : collection) {
          encode(item, dest);
        }

      } else if (object instanceof ParseRelation) {// TODO
        throw new IllegalArgumentException("Not supported yet.");

      } else if (object == JSONObject.NULL) {
        dest.writeString(TYPE_JSON_NULL);

      } else if (object == null) {
        dest.writeString(TYPE_NULL);

      // String, Number, Boolean. Simply use writeValue
      } else if (isValidType(object)) {
        dest.writeString(TYPE_NATIVE);
        dest.writeValue(object);

      } else {
        throw new IllegalArgumentException("Could not encode this object into Parcel. "
            + object.getClass().toString());
      }

    } catch (Exception e) {
      throw new IllegalArgumentException("Could not encode this object into Parcel. "
            + object.getClass().toString());
    }
  }

  protected void encodeParseObject(ParseObject object, Parcel dest) {
    dest.writeParcelable(object, 0);
  }
}

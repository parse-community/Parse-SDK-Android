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

import org.json.JSONObject;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * A {@code ParseParcelableEncoder} can be used to parcel objects into a {@link android.os.Parcel}.
 *
 * This is capable of parceling {@link ParseObject}s, but the result can likely be a
 * {@link StackOverflowError} due to circular references in the objects tree.
 * When needing to parcel {@link ParseObject}, use the stateful {@link ParseObjectParcelEncoder}.
 *
 * @see ParseParcelDecoder
 * @see ParseObjectParcelEncoder
 */
/* package */ class ParseParcelEncoder {

  // This class isn't really a Singleton, but since it has no state, it's more efficient to get the
  // default instance.
  private static final ParseParcelEncoder INSTANCE = new ParseParcelEncoder();
  public static ParseParcelEncoder get() {
    return INSTANCE;
  }

  private static boolean isValidType(Object value) {
    // This encodes to parcel what ParseEncoder does for JSON
    return ParseEncoder.isValidType(value);
  }

  /* package */ final static String TYPE_OBJECT = "Object";
  /* package */ final static String TYPE_POINTER = "Pointer";
  /* package */ final static String TYPE_DATE = "Date";
  /* package */ final static String TYPE_BYTES = "Bytes";
  /* package */ final static String TYPE_ACL = "Acl";
  /* package */ final static String TYPE_RELATION = "Relation";
  /* package */ final static String TYPE_MAP = "Map";
  /* package */ final static String TYPE_COLLECTION = "Collection";
  /* package */ final static String TYPE_JSON_NULL = "JsonNull";
  /* package */ final static String TYPE_NULL = "Null";
  /* package */ final static String TYPE_NATIVE = "Native";
  /* package */ final static String TYPE_OP = "Operation";
  /* package */ final static String TYPE_FILE = "File";
  /* package */ final static String TYPE_GEOPOINT = "GeoPoint";
  /* package */ final static String TYPE_POLYGON = "Polygon";

  public void encode(Object object, Parcel dest) {
    try {
      if (object instanceof ParseObject) {
        // By default, encode as a full ParseObject. Overriden in sublasses.
        encodeParseObject((ParseObject) object, dest);

      } else if (object instanceof Date) {
        dest.writeString(TYPE_DATE);
        dest.writeString(ParseDateFormat.getInstance().format((Date) object));

      } else if (object instanceof byte[]) {
        dest.writeString(TYPE_BYTES);
        byte[] bytes = (byte[]) object;
        dest.writeInt(bytes.length);
        dest.writeByteArray(bytes);

      } else if (object instanceof ParseFieldOperation) {
        dest.writeString(TYPE_OP);
        ((ParseFieldOperation) object).encode(dest, this);

      } else if (object instanceof ParseFile) {
        dest.writeString(TYPE_FILE);
        ((ParseFile) object).writeToParcel(dest, this);

      } else if (object instanceof ParseGeoPoint) {
        dest.writeString(TYPE_GEOPOINT);
        ((ParseGeoPoint) object).writeToParcel(dest, this);

      } else if (object instanceof ParsePolygon) {
        dest.writeString(TYPE_POLYGON);
        ((ParsePolygon) object).writeToParcel(dest, this);

      } else if (object instanceof ParseACL) {
        dest.writeString(TYPE_ACL);
        ((ParseACL) object).writeToParcel(dest, this);

      } else if (object instanceof ParseRelation) {
        dest.writeString(TYPE_RELATION);
        ((ParseRelation) object).writeToParcel(dest, this);

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
    dest.writeString(TYPE_OBJECT);
    object.writeToParcel(dest, this);
  }

  protected void encodePointer(String className, String objectId, Parcel dest) {
    dest.writeString(TYPE_POINTER);
    dest.writeString(className);
    dest.writeString(objectId);
  }
}

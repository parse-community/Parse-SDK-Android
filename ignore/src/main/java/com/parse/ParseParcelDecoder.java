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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A {@code ParseParcelableDecoder} can be used to unparcel objects such as
 * {@link com.parse.ParseObject} from a {@link android.os.Parcel}.
 *
 * This is capable of decoding objects and pointers to them.
 * However, for improved behavior in the case of {@link ParseObject}s, use the stateful
 * implementation {@link ParseObjectParcelDecoder}.
 *
 * @see ParseParcelEncoder
 * @see ParseObjectParcelDecoder
 */
/* package */ class ParseParcelDecoder {

  // This class isn't really a Singleton, but since it has no state, it's more efficient to get the
  // default instance.
  private static final ParseParcelDecoder INSTANCE = new ParseParcelDecoder();
  public static ParseParcelDecoder get() {
    return INSTANCE;
  }

  public Object decode(Parcel source) {
    String type = source.readString();
    switch (type) {

      case ParseParcelEncoder.TYPE_OBJECT:
        return decodeParseObject(source);

      case ParseParcelEncoder.TYPE_POINTER:
        return decodePointer(source);

      case ParseParcelEncoder.TYPE_DATE:
        String iso = source.readString();
        return ParseDateFormat.getInstance().parse(iso);

      case ParseParcelEncoder.TYPE_BYTES:
        byte[] bytes = new byte[source.readInt()];
        source.readByteArray(bytes);
        return bytes;

      case ParseParcelEncoder.TYPE_OP:
        return ParseFieldOperations.decode(source, this);

      case ParseParcelEncoder.TYPE_FILE:
        return new ParseFile(source, this);

      case ParseParcelEncoder.TYPE_GEOPOINT:
        return new ParseGeoPoint(source, this);

      case ParseParcelEncoder.TYPE_POLYGON:
        return new ParsePolygon(source, this);
          
      case ParseParcelEncoder.TYPE_ACL:
        return new ParseACL(source, this);

      case ParseParcelEncoder.TYPE_RELATION:
        return new ParseRelation<>(source, this);

      case ParseParcelEncoder.TYPE_MAP:
        int size = source.readInt();
        Map<String, Object> map = new HashMap<>(size);
        for (int i = 0; i < size; i++) {
          map.put(source.readString(), decode(source));
        }
        return map;

      case ParseParcelEncoder.TYPE_COLLECTION:
        int length = source.readInt();
        List<Object> list = new ArrayList<>(length);
        for (int i = 0; i < length; i++) {
          list.add(i, decode(source));
        }
        return list;

      case ParseParcelEncoder.TYPE_JSON_NULL:
        return JSONObject.NULL;

      case ParseParcelEncoder.TYPE_NULL:
        return null;

      case ParseParcelEncoder.TYPE_NATIVE:
        return source.readValue(null); // No need for a class loader.

      default:
        throw new RuntimeException("Could not unparcel objects from this Parcel.");

    }
  }

  protected ParseObject decodeParseObject(Parcel source) {
    return ParseObject.createFromParcel(source, this);
  }

  protected ParseObject decodePointer(Parcel source) {
    // By default, use createWithoutData. Overriden in subclass.
    return ParseObject.createWithoutData(source.readString(), source.readString());
  }

}

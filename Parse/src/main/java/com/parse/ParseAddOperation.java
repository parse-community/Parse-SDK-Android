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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * An operation that adds a new element to an array field.
 */
/** package */ class ParseAddOperation implements ParseFieldOperation {
  /* package */ final static String OP_NAME = "Add";

  protected final ArrayList<Object> objects = new ArrayList<>();

  public ParseAddOperation(Collection<?> coll) {
    objects.addAll(coll);
  }

  @Override
  public JSONObject encode(ParseEncoder objectEncoder) throws JSONException {
    JSONObject output = new JSONObject();
    output.put("__op", OP_NAME);
    output.put("objects", objectEncoder.encode(objects));
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
  public ParseFieldOperation mergeWithPrevious(ParseFieldOperation previous) {
    if (previous == null) {
      return this;
    } else if (previous instanceof ParseDeleteOperation) {
      return new ParseSetOperation(objects);
    } else if (previous instanceof ParseSetOperation) {
      Object value = ((ParseSetOperation) previous).getValue();
      if (value instanceof JSONArray) {
        ArrayList<Object> result = ParseFieldOperations.jsonArrayAsArrayList((JSONArray) value);
        result.addAll(objects);
        return new ParseSetOperation(new JSONArray(result));
      } else if (value instanceof List) {
        ArrayList<Object> result = new ArrayList<>((List<?>) value);
        result.addAll(objects);
        return new ParseSetOperation(result);
      } else {
        throw new IllegalArgumentException("You can only add an item to a List or JSONArray.");
      }
    } else if (previous instanceof ParseAddOperation) {
      ArrayList<Object> result = new ArrayList<>(((ParseAddOperation) previous).objects);
      result.addAll(objects);
      return new ParseAddOperation(result);
    } else {
      throw new IllegalArgumentException("Operation is invalid after previous operation.");
    }
  }

  @Override
  public Object apply(Object oldValue, String key) {
    if (oldValue == null) {
      return objects;
    } else if (oldValue instanceof JSONArray) {
      ArrayList<Object> old = ParseFieldOperations.jsonArrayAsArrayList((JSONArray) oldValue);
      @SuppressWarnings("unchecked")
      ArrayList<Object> newValue = (ArrayList<Object>) this.apply(old, key);
      return new JSONArray(newValue);
    } else if (oldValue instanceof List) {
      ArrayList<Object> result = new ArrayList<>((List<?>) oldValue);
      result.addAll(objects);
      return result;
    } else {
      throw new IllegalArgumentException("Operation is invalid after previous operation.");
    }
  }
}

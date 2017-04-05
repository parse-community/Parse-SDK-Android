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
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * An operation that removes every instance of an element from an array field.
 */
/** package */ class ParseRemoveOperation implements ParseFieldOperation {
  /* package */ final static String OP_NAME = "Remove";

  protected final HashSet<Object> objects = new HashSet<>();

  public ParseRemoveOperation(Collection<?> coll) {
    objects.addAll(coll);
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
    } else if (previous instanceof ParseRemoveOperation) {
      HashSet<Object> result = new HashSet<>(((ParseRemoveOperation) previous).objects);
      result.addAll(objects);
      return new ParseRemoveOperation(result);
    } else {
      throw new IllegalArgumentException("Operation is invalid after previous operation.");
    }
  }

  @Override
  public Object apply(Object oldValue, String key) {
    if (oldValue == null) {
      return new ArrayList<>();
    } else if (oldValue instanceof JSONArray) {
      ArrayList<Object> old = ParseFieldOperations.jsonArrayAsArrayList((JSONArray) oldValue);
      @SuppressWarnings("unchecked")
      ArrayList<Object> newValue = (ArrayList<Object>) this.apply(old, key);
      return new JSONArray(newValue);
    } else if (oldValue instanceof List) {
      ArrayList<Object> result = new ArrayList<>((List<?>) oldValue);
      result.removeAll(objects);

      // Remove the removed objects from "objects" -- the items remaining
      // should be ones that weren't removed by object equality.
      ArrayList<Object> objectsToBeRemoved = new ArrayList<>(objects);
      objectsToBeRemoved.removeAll(result);

      // Build up set of object IDs for any ParseObjects in the remaining objects-to-be-removed
      HashSet<String> objectIds = new HashSet<>();
      for (Object obj : objectsToBeRemoved) {
        if (obj instanceof ParseObject) {
          objectIds.add(((ParseObject) obj).getObjectId());
        }
      }

      // And iterate over "result" to see if any other ParseObjects need to be removed
      Iterator<Object> resultIterator = result.iterator();
      while (resultIterator.hasNext()) {
        Object obj = resultIterator.next();
        if (obj instanceof ParseObject && objectIds.contains(((ParseObject) obj).getObjectId())) {
          resultIterator.remove();
        }
      }
      return result;
    } else {
      throw new IllegalArgumentException("Operation is invalid after previous operation.");
    }
  }
}

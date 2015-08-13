/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * A ParseFieldOperation represents a modification to a value in a ParseObject. For example,
 * setting, deleting, or incrementing a value are all different kinds of ParseFieldOperations.
 * ParseFieldOperations themselves can be considered to be immutable.
 */
/** package */ interface ParseFieldOperation {
  /**
   * Converts the ParseFieldOperation to a data structure (typically a JSONObject) that can be
   * converted to JSON and sent to Parse as part of a save operation.
   * 
   * @param objectEncoder
   *          An object responsible for serializing ParseObjects.
   * @return An object to be jsonified.
   */
  Object encode(ParseEncoder objectEncoder) throws JSONException;

  /**
   * Returns a field operation that is composed of a previous operation followed by this operation.
   * This will not mutate either operation. However, it may return self if the current operation is
   * not affected by previous changes. For example:
   * 
   * <pre>
   * {increment by 2}.mergeWithPrevious({set to 5}) -> {set to 7}
   * {set to 5}.mergeWithPrevious({increment by 2}) -> {set to 5}
   * {add "foo"}.mergeWithPrevious({delete}) -> {set to ["foo"]}
   * {delete}.mergeWithPrevious({add "foo"}) -> {delete}
   * </pre>
   * 
   * @param previous
   *          The most recent operation on the field, or null if none.
   * @return A new ParseFieldOperation or this.
   */
  ParseFieldOperation mergeWithPrevious(ParseFieldOperation previous);

  /**
   * Returns a new estimated value based on a previous value and this operation. This value is not
   * intended to be sent to Parse, but it used locally on the client to inspect the most likely
   * current value for a field. The key and object are used solely for ParseRelation to be able to
   * construct objects that refer back to its parent.
   * 
   * @param oldValue
   *          The previous value for the field.
   * @param key
   *          The key that this value is for.
   * @return The new value for the field.
   */
  Object apply(Object oldValue, String key);
}

final class ParseFieldOperations {
  private ParseFieldOperations() {
  }

  /**
   * A function that creates a ParseFieldOperation from a JSONObject.
   */
  private interface ParseFieldOperationFactory {
    ParseFieldOperation decode(JSONObject object, ParseDecoder decoder) throws JSONException;
  }

  // A map of all known decoders.
  private static Map<String, ParseFieldOperationFactory> opDecoderMap = new HashMap<>();

  /**
   * Registers a single factory for a given __op field value.
   */
  private static void registerDecoder(String opName, ParseFieldOperationFactory factory) {
    opDecoderMap.put(opName, factory);
  }

  /**
   * Registers a list of default decoder functions that convert a JSONObject with an __op field into
   * a ParseFieldOperation.
   */
  static void registerDefaultDecoders() {
    registerDecoder("Batch", new ParseFieldOperationFactory() {
      @Override
      public ParseFieldOperation decode(JSONObject object, ParseDecoder decoder)
          throws JSONException {
        ParseFieldOperation op = null;
        JSONArray ops = object.getJSONArray("ops");
        for (int i = 0; i < ops.length(); ++i) {
          ParseFieldOperation nextOp = ParseFieldOperations.decode(ops.getJSONObject(i), decoder);
          op = nextOp.mergeWithPrevious(op);
        }
        return op;
      }
    });

    registerDecoder("Delete", new ParseFieldOperationFactory() {
      @Override
      public ParseFieldOperation decode(JSONObject object, ParseDecoder decoder)
          throws JSONException {
        return ParseDeleteOperation.getInstance();
      }
    });

    registerDecoder("Increment", new ParseFieldOperationFactory() {
      @Override
      public ParseFieldOperation decode(JSONObject object, ParseDecoder decoder)
          throws JSONException {
        return new ParseIncrementOperation((Number) decoder.decode(object.opt("amount")));
      }
    });

    registerDecoder("Add", new ParseFieldOperationFactory() {
      @Override
      public ParseFieldOperation decode(JSONObject object, ParseDecoder decoder)
          throws JSONException {
        return new ParseAddOperation((Collection) decoder.decode(object.opt("objects")));
      }
    });

    registerDecoder("AddUnique", new ParseFieldOperationFactory() {
      @Override
      public ParseFieldOperation decode(JSONObject object, ParseDecoder decoder)
          throws JSONException {
        return new ParseAddUniqueOperation((Collection) decoder.decode(object.opt("objects")));
      }
    });

    registerDecoder("Remove", new ParseFieldOperationFactory() {
      @Override
      public ParseFieldOperation decode(JSONObject object, ParseDecoder decoder)
          throws JSONException {
        return new ParseRemoveOperation((Collection) decoder.decode(object.opt("objects")));
      }
    });

    registerDecoder("AddRelation", new ParseFieldOperationFactory() {
      @Override
      public ParseFieldOperation decode(JSONObject object, ParseDecoder decoder)
          throws JSONException {
        JSONArray objectsArray = object.optJSONArray("objects");
        List<ParseObject> objectsList = (List<ParseObject>) decoder.decode(objectsArray);
        return new ParseRelationOperation<>(new HashSet<>(objectsList), null);
      }
    });

    registerDecoder("RemoveRelation", new ParseFieldOperationFactory() {
      @Override
      public ParseFieldOperation decode(JSONObject object, ParseDecoder decoder)
          throws JSONException {
        JSONArray objectsArray = object.optJSONArray("objects");
        List<ParseObject> objectsList = (List<ParseObject>) decoder.decode(objectsArray);
        return new ParseRelationOperation<>(null, new HashSet<>(objectsList));
      }
    });
  }

  /**
   * Converts a parsed JSON object into a PFFieldOperation.
   * 
   * @param encoded
   *          A JSONObject containing an __op field.
   * @return A PFFieldOperation.
   */
  static ParseFieldOperation decode(JSONObject encoded, ParseDecoder decoder) throws JSONException {
    String op = encoded.optString("__op");
    ParseFieldOperationFactory factory = opDecoderMap.get(op);
    if (factory == null) {
      throw new RuntimeException("Unable to decode operation of type " + op);
    }
    return factory.decode(encoded, decoder);
  }

  /**
   * Converts a JSONArray into an ArrayList.
   */
  static ArrayList<Object> jsonArrayAsArrayList(JSONArray array) {
    ArrayList<Object> result = new ArrayList<>(array.length());
    for (int i = 0; i < array.length(); ++i) {
      try {
        result.add(array.get(i));
      } catch (JSONException e) {
        // This can't actually happen.
        throw new RuntimeException(e);
      }
    }
    return result;
  }
}

package com.parse;

import android.os.Parcel;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Utility methods to deal with {@link ParseFieldOperation} decoding, both from JSON objects and
 * from {@link Parcel}s.
 */
/* package */ final class ParseFieldOperations {
  private ParseFieldOperations() {
  }

  /**
   * A function that creates a ParseFieldOperation from a JSONObject or a Parcel.
   */
  private interface ParseFieldOperationFactory {
    ParseFieldOperation decode(JSONObject object, ParseDecoder decoder) throws JSONException;
    ParseFieldOperation decode(Parcel source, ParseParcelDecoder decoder);
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
   * Registers a list of default decoder functions that convert a JSONObject with an __op field,
   * or a Parcel with a op name string, into a ParseFieldOperation.
   */
  static void registerDefaultDecoders() {
    registerDecoder(ParseRelationOperation.OP_NAME_BATCH, new ParseFieldOperationFactory() {
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

      @Override
      public ParseFieldOperation decode(Parcel source, ParseParcelDecoder decoder) {
        // Decode AddRelation and then RemoveRelation
        ParseFieldOperation add = ParseFieldOperations.decode(source, decoder);
        ParseFieldOperation remove = ParseFieldOperations.decode(source, decoder);
        return remove.mergeWithPrevious(add);
      }
    });

    registerDecoder(ParseDeleteOperation.OP_NAME, new ParseFieldOperationFactory() {
      @Override
      public ParseFieldOperation decode(JSONObject object, ParseDecoder decoder)
          throws JSONException {
        return ParseDeleteOperation.getInstance();
      }

      @Override
      public ParseFieldOperation decode(Parcel source, ParseParcelDecoder decoder) {
        return ParseDeleteOperation.getInstance();
      }
    });

    registerDecoder(ParseIncrementOperation.OP_NAME, new ParseFieldOperationFactory() {
      @Override
      public ParseFieldOperation decode(JSONObject object, ParseDecoder decoder)
          throws JSONException {
        return new ParseIncrementOperation((Number) decoder.decode(object.opt("amount")));
      }

      @Override
      public ParseFieldOperation decode(Parcel source, ParseParcelDecoder decoder) {
        return new ParseIncrementOperation((Number) decoder.decode(source));
      }
    });

    registerDecoder(ParseAddOperation.OP_NAME, new ParseFieldOperationFactory() {
      @Override
      public ParseFieldOperation decode(JSONObject object, ParseDecoder decoder)
          throws JSONException {
        return new ParseAddOperation((Collection) decoder.decode(object.opt("objects")));
      }

      @Override
      public ParseFieldOperation decode(Parcel source, ParseParcelDecoder decoder) {
        int size = source.readInt();
        List<Object> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
          list.add(i, decoder.decode(source));
        }
        return new ParseAddOperation(list);
      }
    });

    registerDecoder(ParseAddUniqueOperation.OP_NAME, new ParseFieldOperationFactory() {
      @Override
      public ParseFieldOperation decode(JSONObject object, ParseDecoder decoder)
          throws JSONException {
        return new ParseAddUniqueOperation((Collection) decoder.decode(object.opt("objects")));
      }

      @Override
      public ParseFieldOperation decode(Parcel source, ParseParcelDecoder decoder) {
        int size = source.readInt();
        List<Object> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
          list.add(i, decoder.decode(source));
        }
        return new ParseAddUniqueOperation(list);
      }
    });

    registerDecoder(ParseRemoveOperation.OP_NAME, new ParseFieldOperationFactory() {
      @Override
      public ParseFieldOperation decode(JSONObject object, ParseDecoder decoder)
          throws JSONException {
        return new ParseRemoveOperation((Collection) decoder.decode(object.opt("objects")));
      }

      @Override
      public ParseFieldOperation decode(Parcel source, ParseParcelDecoder decoder) {
        int size = source.readInt();
        List<Object> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
          list.add(i, decoder.decode(source));
        }
        return new ParseRemoveOperation(list);
      }
    });

    registerDecoder(ParseRelationOperation.OP_NAME_ADD, new ParseFieldOperationFactory() {
      @Override
      public ParseFieldOperation decode(JSONObject object, ParseDecoder decoder)
          throws JSONException {
        JSONArray objectsArray = object.optJSONArray("objects");
        List<ParseObject> objectsList = (List<ParseObject>) decoder.decode(objectsArray);
        return new ParseRelationOperation<>(new HashSet<>(objectsList), null);
      }

      @Override
      public ParseFieldOperation decode(Parcel source, ParseParcelDecoder decoder) {
        int size = source.readInt();
        Set<ParseObject> set = new HashSet<>(size);
        for (int i = 0; i < size; i++) {
          set.add((ParseObject) decoder.decode(source));
        }
        return new ParseRelationOperation<>(set, null);
      }
    });

    registerDecoder(ParseRelationOperation.OP_NAME_REMOVE, new ParseFieldOperationFactory() {
      @Override
      public ParseFieldOperation decode(JSONObject object, ParseDecoder decoder)
          throws JSONException {
        JSONArray objectsArray = object.optJSONArray("objects");
        List<ParseObject> objectsList = (List<ParseObject>) decoder.decode(objectsArray);
        return new ParseRelationOperation<>(null, new HashSet<>(objectsList));
      }

      @Override
      public ParseFieldOperation decode(Parcel source, ParseParcelDecoder decoder) {
        int size = source.readInt();
        Set<ParseObject> set = new HashSet<>(size);
        for (int i = 0; i < size; i++) {
          set.add((ParseObject) decoder.decode(source));
        }
        return new ParseRelationOperation<>(null, set);
      }
    });

    registerDecoder(ParseSetOperation.OP_NAME, new ParseFieldOperationFactory() {
      @Override
      public ParseFieldOperation decode(JSONObject object, ParseDecoder decoder) throws JSONException {
        return null; // Not called.
      }

      @Override
      public ParseFieldOperation decode(Parcel source, ParseParcelDecoder decoder) {
        return new ParseSetOperation(decoder.decode(source));
      }
    });
  }

  /**
   * Converts a parsed JSON object into a ParseFieldOperation.
   *
   * @param encoded
   *          A JSONObject containing an __op field.
   * @return A ParseFieldOperation.
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
   * Reads a ParseFieldOperation out of the given Parcel.
   *
   * @param source
   *          The source Parcel.
   * @param decoder
   *          The given ParseParcelableDecoder.
   *
   * @return A ParseFieldOperation.
   */
  static ParseFieldOperation decode(Parcel source, ParseParcelDecoder decoder) {
    String op = source.readString();
    ParseFieldOperationFactory factory = opDecoderMap.get(op);
    if (factory == null) {
      throw new RuntimeException("Unable to decode operation of type " + op);
    }
    return factory.decode(source, decoder);
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

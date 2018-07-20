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

import org.json.JSONException;

/**
 * A ParseFieldOperation represents a modification to a value in a ParseObject. For example,
 * setting, deleting, or incrementing a value are all different kinds of ParseFieldOperations.
 * ParseFieldOperations themselves can be considered to be immutable.
 */
interface ParseFieldOperation {
    /**
     * Converts the ParseFieldOperation to a data structure (typically a JSONObject) that can be
     * converted to JSON and sent to Parse as part of a save operation.
     *
     * @param objectEncoder An object responsible for serializing ParseObjects.
     * @return An object to be jsonified.
     */
    Object encode(ParseEncoder objectEncoder) throws JSONException;

    /**
     * Writes the ParseFieldOperation to the given Parcel using the given encoder.
     *
     * @param dest              The destination Parcel.
     * @param parcelableEncoder A ParseParcelableEncoder.
     */
    void encode(Parcel dest, ParseParcelEncoder parcelableEncoder);

    /**
     * Returns a field operation that is composed of a previous operation followed by this operation.
     * This will not mutate either operation. However, it may return self if the current operation is
     * not affected by previous changes. For example:
     * <p>
     * <pre>
     * {increment by 2}.mergeWithPrevious({set to 5}) -> {set to 7}
     * {set to 5}.mergeWithPrevious({increment by 2}) -> {set to 5}
     * {add "foo"}.mergeWithPrevious({delete}) -> {set to ["foo"]}
     * {delete}.mergeWithPrevious({add "foo"}) -> {delete}
     * </pre>
     *
     * @param previous The most recent operation on the field, or null if none.
     * @return A new ParseFieldOperation or this.
     */
    ParseFieldOperation mergeWithPrevious(ParseFieldOperation previous);

    /**
     * Returns a new estimated value based on a previous value and this operation. This value is not
     * intended to be sent to Parse, but it used locally on the client to inspect the most likely
     * current value for a field. The key and object are used solely for ParseRelation to be able to
     * construct objects that refer back to its parent.
     *
     * @param oldValue The previous value for the field.
     * @param key      The key that this value is for.
     * @return The new value for the field.
     */
    Object apply(Object oldValue, String key);
}


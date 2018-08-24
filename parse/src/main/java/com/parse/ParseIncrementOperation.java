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
import org.json.JSONObject;

/**
 * An operation that increases a numeric field's value by a given amount.
 */
class ParseIncrementOperation implements ParseFieldOperation {
    /* package */ final static String OP_NAME = "Increment";

    private final Number amount;

    public ParseIncrementOperation(Number amount) {
        this.amount = amount;
    }

    @Override
    public JSONObject encode(ParseEncoder objectEncoder) throws JSONException {
        JSONObject output = new JSONObject();
        output.put("__op", OP_NAME);
        output.put("amount", amount);
        return output;
    }

    @Override
    public void encode(Parcel dest, ParseParcelEncoder parcelableEncoder) {
        dest.writeString(OP_NAME);
        parcelableEncoder.encode(amount, dest); // Let encoder figure out how to parcel Number
    }

    @Override
    public ParseFieldOperation mergeWithPrevious(ParseFieldOperation previous) {
        if (previous == null) {
            return this;
        } else if (previous instanceof ParseDeleteOperation) {
            return new ParseSetOperation(amount);
        } else if (previous instanceof ParseSetOperation) {
            Object oldValue = ((ParseSetOperation) previous).getValue();
            if (oldValue instanceof Number) {
                return new ParseSetOperation(Numbers.add((Number) oldValue, amount));
            } else {
                throw new IllegalArgumentException("You cannot increment a non-number.");
            }
        } else if (previous instanceof ParseIncrementOperation) {
            Number oldAmount = ((ParseIncrementOperation) previous).amount;
            return new ParseIncrementOperation(Numbers.add(oldAmount, amount));
        } else {
            throw new IllegalArgumentException("Operation is invalid after previous operation.");
        }
    }

    @Override
    public Object apply(Object oldValue, String key) {
        if (oldValue == null) {
            return amount;
        } else if (oldValue instanceof Number) {
            return Numbers.add((Number) oldValue, amount);
        } else {
            throw new IllegalArgumentException("You cannot increment a non-number.");
        }
    }
}

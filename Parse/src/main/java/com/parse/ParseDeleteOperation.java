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
 * An operation where a field is deleted from the object.
 */
/** package */ class ParseDeleteOperation implements ParseFieldOperation {
  /* package */ final static String OP_NAME = "Delete";

  private static final ParseDeleteOperation defaultInstance = new ParseDeleteOperation();

  public static ParseDeleteOperation getInstance() {
    return defaultInstance;
  }

  private ParseDeleteOperation() {
  }

  @Override
  public JSONObject encode(ParseEncoder objectEncoder) throws JSONException {
    JSONObject output = new JSONObject();
    output.put("__op", OP_NAME);
    return output;
  }

  @Override
  public void encode(Parcel dest, ParseParcelEncoder parcelableEncoder) {
    dest.writeString(OP_NAME);
  }

  @Override
  public ParseFieldOperation mergeWithPrevious(ParseFieldOperation previous) {
    return this;
  }

  @Override
  public Object apply(Object oldValue, String key) {
    return null;
  }
}

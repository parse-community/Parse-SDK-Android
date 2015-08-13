/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import org.json.JSONException;
import org.json.JSONObject;

// A ParseJSONCacheItem is a pairing of a json string with its hash value.
/** package */ class ParseJSONCacheItem {
  private JSONObject json;
  private String hashValue;

  public ParseJSONCacheItem(Object object) throws JSONException {
    json = new JSONObject();
    json.put("object", PointerOrLocalIdEncoder.get().encode(object));
    hashValue = ParseDigestUtils.md5(json.toString());
  }

  public boolean equals(ParseJSONCacheItem other) {
    return hashValue.equals(other.getHashValue());
  }

  public String getHashValue() {
    return hashValue;
  }

  public Object getJSONObject() {
    try {
      return json.get("object");
    } catch (JSONException e) {
      return null;
    }
  }
}

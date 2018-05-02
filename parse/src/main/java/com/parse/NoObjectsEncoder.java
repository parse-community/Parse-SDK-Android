/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import org.json.JSONObject;

/**
 * Throws an exception if someone attemps to encode a {@code ParseObject}.
 */
/** package */ class NoObjectsEncoder extends ParseEncoder {

  // This class isn't really a Singleton, but since it has no state, it's more efficient to get the
  // default instance.
  private static final NoObjectsEncoder INSTANCE = new NoObjectsEncoder();
  public static NoObjectsEncoder get() {
    return INSTANCE;
  }

  @Override
  public JSONObject encodeRelatedObject(ParseObject object) {
    throw new IllegalArgumentException("ParseObjects not allowed here");
  }
}

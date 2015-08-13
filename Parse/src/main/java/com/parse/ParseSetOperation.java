/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

/**
 * An operation where a field is set to a given value regardless of its previous value.
 */
/** package */ class ParseSetOperation implements ParseFieldOperation {
  private final Object value;

  public ParseSetOperation(Object newValue) {
    value = newValue;
  }

  public Object getValue() {
    return value;
  }

  @Override
  public Object encode(ParseEncoder objectEncoder) {
    return objectEncoder.encode(value);
  }

  @Override
  public ParseFieldOperation mergeWithPrevious(ParseFieldOperation previous) {
    return this;
  }

  @Override
  public Object apply(Object oldValue, String key) {
    return value;
  }
}

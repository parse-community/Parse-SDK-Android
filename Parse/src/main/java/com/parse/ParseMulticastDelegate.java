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
import java.util.LinkedList;
import java.util.List;

/** package */ class ParseMulticastDelegate<T> {
  private final List<ParseCallback2<T, ParseException>> callbacks;

  public ParseMulticastDelegate() {
    callbacks = new LinkedList<>();
  }

  public void subscribe(ParseCallback2<T, ParseException> callback) {
    callbacks.add(callback);
  }

  public void unsubscribe(ParseCallback2<T, ParseException> callback) {
    callbacks.remove(callback);
  }

  public void invoke(T result, ParseException exception) {
    for (ParseCallback2<T, ParseException> callback : new ArrayList<>(callbacks)) {
      callback.done(result, exception);
    }
  }

  public void clear() {
    callbacks.clear();
  }
}

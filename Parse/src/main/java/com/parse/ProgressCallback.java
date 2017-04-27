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
 * A {@code ProgressCallback} is used to get upload or download progress of a {@link ParseFile}
 * action.
 * <p/>
 * The easiest way to use a {@code ProgressCallback} is through an anonymous inner class.
 */
// FYI, this does not extend ParseCallback2 since it does not match the usual signature
// done(T, ParseException), but is done(T).
public interface ProgressCallback {
  /**
   * Override this function with your desired callback.
   */
  void done(Integer percentDone);
}

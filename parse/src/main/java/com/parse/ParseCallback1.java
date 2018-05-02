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
 * A {@code ParseCallback} is used to do something after a background task completes. End users will
 * use a specific subclass of {@code ParseCallback}.
 */
/** package */ interface ParseCallback1<T extends Throwable> {
  /**
   * {@code done(t)} must be overridden when you are doing a background operation. It is called
   * when the background operation completes.
   * <p/>
   * If the operation is successful, {@code t} will be {@code null}.
   * <p/>
   * If the operation was unsuccessful, {@code t} will contain information about the operation
   * failure.
   *
   * @param t
   *          Generally an {@link Throwable} that was thrown by the operation, if there was any.
   */
  void done(T t);
}

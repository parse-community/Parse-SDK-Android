/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import bolts.Task;

/** package */ interface ParseObjectCurrentController<T extends ParseObject> {

  /**
   * Persist the currentParseObject
   * @param object
   * @return
   */
  Task<Void> setAsync(T object);

  /**
   * Get the persisted currentParseObject
   * @return
   */
  Task<T> getAsync();

  /**
   * Check whether the currentParseObject exists or not
   * @return
   */
  Task<Boolean> existsAsync();

  /**
   * Judge whether the given ParseObject is the currentParseObject
   * @param object
   * @return {@code true} if the give {@link ParseObject} is the currentParseObject
   */
  boolean isCurrent(T object);

  /**
   * A test helper to reset the current ParseObject. This method nullifies the in memory
   * currentParseObject
   */
  void clearFromMemory();

  /**
   * A test helper to reset the current ParseObject. This method nullifies the in memory and in
   * disk currentParseObject
   */
  void clearFromDisk();
}

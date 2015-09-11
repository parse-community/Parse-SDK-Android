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

/** package */ interface ParseCurrentUserController
    extends ParseObjectCurrentController<ParseUser> {

  /**
   * Gets the persisted current ParseUser.
   * @param shouldAutoCreateUser
   * @return
   */
  Task<ParseUser> getAsync(boolean shouldAutoCreateUser);

  /**
   * Sets the persisted current ParseUser only if it's current or we're not synced with disk.
   * @param user
   * @return
   */
  Task<Void> setIfNeededAsync(ParseUser user);

  /**
   * Gets the session token of the persisted current ParseUser.
   * @return
   */
  Task<String> getCurrentSessionTokenAsync();

  /**
   * Logs out the current ParseUser.
   * @return
   */
  Task<Void> logOutAsync();
}

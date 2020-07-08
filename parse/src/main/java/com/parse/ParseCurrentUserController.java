/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import com.parse.boltsinternal.Task;

interface ParseCurrentUserController
        extends ParseObjectCurrentController<ParseUser> {

    /**
     * Gets the persisted current ParseUser.
     *
     * @param shouldAutoCreateUser should auto create user
     * @return task
     */
    Task<ParseUser> getAsync(boolean shouldAutoCreateUser);

    /**
     * Sets the persisted current ParseUser only if it's current or we're not synced with disk.
     *
     * @param user user
     * @return task
     */
    Task<Void> setIfNeededAsync(ParseUser user);

    /**
     * Gets the session token of the persisted current ParseUser.
     *
     * @return task
     */
    Task<String> getCurrentSessionTokenAsync();

    /**
     * Logs out the current ParseUser.
     *
     * @return task
     */
    Task<Void> logOutAsync();
}

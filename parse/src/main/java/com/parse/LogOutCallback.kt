/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse

/**
 * A `LogOutCallback` is used to run code after logging out a user.
 *
 *
 * The easiest way to use a `LogOutCallback` is through an anonymous inner class. Override the
 * `done` function to specify what the callback should do after the login is complete.
 * The `done` function will be run in the UI thread, while the login happens in a
 * background thread. This ensures that the UI does not freeze while the save happens.
 *
 *
 * For example, this sample code logs out a user and calls a different function depending on whether
 * the log out succeeded or not.
 *
 *
 * <pre>
 * ParseUser.logOutInBackground(new LogOutCallback() {
 * public void done(ParseException e) {
 * if (e == null) {
 * logOutSuccessful();
 * } else {
 * somethingWentWrong();
 * }
 * }
 * });
</pre> *
 */
internal interface LogOutCallback : ParseCallback1<ParseException?> {
    /**
     * Override this function with the code you want to run after the save is complete.
     *
     * @param e The exception raised by the log out, or `null` if it succeeded.
     */
    override fun done(e: ParseException?)
}
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
 * A {@code LogOutCallback} is used to run code after logging out a user.
 * <p/>
 * The easiest way to use a {@code LogOutCallback} is through an anonymous inner class. Override the
 * {@code done} function to specify what the callback should do after the login is complete.
 * The {@code done} function will be run in the UI thread, while the login happens in a
 * background thread. This ensures that the UI does not freeze while the save happens.
 * <p/>
 * For example, this sample code logs out a user and calls a different function depending on whether
 * the log out succeeded or not.
 * <p/>
 * <pre>
 * ParseUser.logOutInBackground(new LogOutCallback() {
 *   public void done(ParseException e) {
 *     if (e == null) {
 *       logOutSuccessful();
 *     } else {
 *       somethingWentWrong();
 *     }
 *   }
 * });
 * </pre>
 */
public interface LogOutCallback extends ParseCallback1<ParseException> {
  /**
   * Override this function with the code you want to run after the save is complete.
   *
   * @param e
   *          The exception raised by the log out, or {@code null} if it succeeded.
   */
  @Override
  void done(ParseException e);
}

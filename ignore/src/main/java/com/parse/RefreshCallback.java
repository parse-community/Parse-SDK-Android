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
 * A {@code RefreshCallback} is used to run code after refresh is used to update a {@link ParseObject} in a
 * background thread.
 * <p/>
 * The easiest way to use a {@code RefreshCallback} is through an anonymous inner class. Override
 * the {@code done} function to specify what the callback should do after the refresh is complete.
 * The {@code done} function will be run in the UI thread, while the refresh happens in a
 * background thread. This ensures that the UI does not freeze while the refresh happens.
 * <p/>
 * For example, this sample code refreshes an object of class {@code "MyClass"} and id
 * {@code myId}. It calls a different function depending on whether the refresh succeeded or
 * not.
 * <p/>
 * <pre>
 * object.refreshInBackground(new RefreshCallback() {
 *   public void done(ParseObject object, ParseException e) {
 *     if (e == null) {
 *       objectWasRefreshedSuccessfully(object);
 *     } else {
 *       objectRefreshFailed();
 *     }
 *   }
 * });
 * </pre>
 */
public interface RefreshCallback extends ParseCallback2<ParseObject, ParseException> {
  /**
   * Override this function with the code you want to run after the save is complete.
   *
   * @param object
   *          The object that was refreshed, or {@code null} if it did not succeed.
   * @param e
   *          The exception raised by the login, or {@code null} if it succeeded.
   */
  @Override
  void done(ParseObject object, ParseException e);
}

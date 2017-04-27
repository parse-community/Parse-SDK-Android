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
 * A {@code SignUpCallback} is used to run code after signing up a {@link ParseUser} in a background
 * thread.
 * <p>
 * The easiest way to use a {@code SignUpCallback} is through an anonymous inner class. Override the
 * {@code done} function to specify what the callback should do after the save is complete. The
 * {@code done} function will be run in the UI thread, while the signup happens in a background
 * thread. This ensures that the UI does not freeze while the signup happens.
 * <p>
 * For example, this sample code signs up the object {@code myUser} and calls a different
 * function depending on whether the signup succeeded or not.
 * <p>
 * 
 * <pre>
 * myUser.signUpInBackground(new SignUpCallback() {
 *   public void done(ParseException e) {
 *     if (e == null) {
 *       myUserSignedUpSuccessfully();
 *     } else {
 *       myUserSignUpDidNotSucceed();
 *     }
 *   }
 * });
 * </pre>
 */
public interface SignUpCallback extends ParseCallback1<ParseException> {
  /**
   * Override this function with the code you want to run after the signUp is complete.
   *
   * @param e
   *          The exception raised by the signUp, or {@code null} if it succeeded.
   */
  @Override
  void done(ParseException e);
}

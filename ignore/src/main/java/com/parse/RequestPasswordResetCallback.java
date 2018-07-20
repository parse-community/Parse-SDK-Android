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
 * A {@code RequestPasswordResetCallback} is used to run code requesting a password reset for a
 * user.
 * <p/>
 * The easiest way to use a {@code RequestPasswordResetCallback} is through an anonymous inner
 * class. Override the {@code done} function to specify what the callback should do after the
 * request is complete. The {@code done} function will be run in the UI thread, while the request
 * happens in a background thread. This ensures that the UI does not freeze while the request
 * happens.
 * <p/>
 * For example, this sample code requests a password reset for a user and calls a different function
 * depending on whether the request succeeded or not.
 * <p/>
 * <pre>
 * ParseUser.requestPasswordResetInBackground(&quot;forgetful@example.com&quot;,
 *     new RequestPasswordResetCallback() {
 *       public void done(ParseException e) {
 *         if (e == null) {
 *           requestedSuccessfully();
 *         } else {
 *           requestDidNotSucceed();
 *         }
 *       }
 *     });
 * </pre>
 */
public interface RequestPasswordResetCallback extends ParseCallback1<ParseException> {
  /**
   * Override this function with the code you want to run after the request is complete.
   * 
   * @param e
   *          The exception raised by the save, or {@code null} if no account is associated with the
   *          email address.
   */
  @Override
  void done(ParseException e);
}

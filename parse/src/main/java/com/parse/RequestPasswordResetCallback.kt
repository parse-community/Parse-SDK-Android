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
 * A `RequestPasswordResetCallback` is used to run code requesting a password reset for a
 * user.
 *
 *
 * The easiest way to use a `RequestPasswordResetCallback` is through an anonymous inner
 * class. Override the `done` function to specify what the callback should do after the
 * request is complete. The `done` function will be run in the UI thread, while the request
 * happens in a background thread. This ensures that the UI does not freeze while the request
 * happens.
 *
 *
 * For example, this sample code requests a password reset for a user and calls a different function
 * depending on whether the request succeeded or not.
 *
 *
 * <pre>
 * ParseUser.requestPasswordResetInBackground(&quot;forgetful@example.com&quot;,
 * new RequestPasswordResetCallback() {
 * public void done(ParseException e) {
 * if (e == null) {
 * requestedSuccessfully();
 * } else {
 * requestDidNotSucceed();
 * }
 * }
 * });
</pre> *
 */
internal interface RequestPasswordResetCallback : ParseCallback1<ParseException?> {
    /**
     * Override this function with the code you want to run after the request is complete.
     *
     * @param e The exception raised by the save, or `null` if no account is associated with the
     * email address.
     */
    override fun done(e: ParseException?)
}
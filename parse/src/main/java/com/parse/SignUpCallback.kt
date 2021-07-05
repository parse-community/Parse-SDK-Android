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
 * A `SignUpCallback` is used to run code after signing up a [ParseUser] in a background
 * thread.
 *
 *
 * The easiest way to use a `SignUpCallback` is through an anonymous inner class. Override the
 * `done` function to specify what the callback should do after the save is complete. The
 * `done` function will be run in the UI thread, while the signup happens in a background
 * thread. This ensures that the UI does not freeze while the signup happens.
 *
 *
 * For example, this sample code signs up the object `myUser` and calls a different
 * function depending on whether the signup succeeded or not.
 *
 *
 *
 *
 * <pre>
 * myUser.signUpInBackground(new SignUpCallback() {
 * public void done(ParseException e) {
 * if (e == null) {
 * myUserSignedUpSuccessfully();
 * } else {
 * myUserSignUpDidNotSucceed();
 * }
 * }
 * });
</pre> *
 */
internal interface SignUpCallback : ParseCallback1<ParseException?> {
    /**
     * Override this function with the code you want to run after the signUp is complete.
     *
     * @param e The exception raised by the signUp, or `null` if it succeeded.
     */
    override fun done(e: ParseException?)
}
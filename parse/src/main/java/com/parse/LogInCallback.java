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
 * A {@code LogInCallback} is used to run code after logging in a user.
 * <p/>
 * The easiest way to use a {@code LogInCallback} is through an anonymous inner class. Override the
 * {@code done} function to specify what the callback should do after the login is complete.
 * The {@code done} function will be run in the UI thread, while the login happens in a
 * background thread. This ensures that the UI does not freeze while the save happens.
 * <p/>
 * For example, this sample code logs in a user and calls a different function depending on whether
 * the login succeeded or not.
 * <p/>
 * <pre>
 * ParseUser.logInInBackground(&quot;username&quot;, &quot;password&quot;, new LogInCallback() {
 *   public void done(ParseUser user, ParseException e) {
 *     if (e == null &amp;&amp; user != null) {
 *       loginSuccessful();
 *     } else if (user == null) {
 *       usernameOrPasswordIsInvalid();
 *     } else {
 *       somethingWentWrong();
 *     }
 *   }
 * });
 * </pre>
 */
public interface LogInCallback extends ParseCallback2<ParseUser, ParseException> {
    /**
     * Override this function with the code you want to run after the save is complete.
     *
     * @param user The user that logged in, if the username and password is valid.
     * @param e    The exception raised by the login, or {@code null} if it succeeded.
     */
    @Override
    void done(ParseUser user, ParseException e);
}

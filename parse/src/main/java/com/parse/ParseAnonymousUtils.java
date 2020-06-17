/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.parse.boltsinternal.Task;

/**
 * Provides utility functions for working with Anonymously logged-in users. Anonymous users have
 * some unique characteristics:
 * <ul>
 * <li>Anonymous users don't need a user name or password.</li>
 * <li>Once logged out, an anonymous user cannot be recovered.</li>
 * <li>When the current user is anonymous, the following methods can be used to switch to a
 * different user or convert the anonymous user into a regular one:
 * <ul>
 * <li>signUp converts an anonymous user to a standard user with the given username and password.
 * Data associated with the anonymous user is retained.</li>
 * <li>logIn switches users without converting the anonymous user. Data associated with the
 * anonymous user will be lost.</li>
 * <li>Service logIn (e.g. Facebook, Twitter) will attempt to convert the anonymous user into a
 * standard user by linking it to the service. If a user already exists that is linked to the
 * service, it will instead switch to the existing user.</li>
 * <li>Service linking (e.g. Facebook, Twitter) will convert the anonymous user into a standard user
 * by linking it to the service.</li>
 * </ul>
 * </ul>
 */
@SuppressWarnings("unused")
public final class ParseAnonymousUtils {
    /* package */ static final String AUTH_TYPE = "anonymous";

    private ParseAnonymousUtils() {
        // do nothing
    }

    /**
     * Whether the user is logged in anonymously.
     *
     * @param user User to check for anonymity. The user must be logged in on this device.
     * @return True if the user is anonymous. False if the user is not the current user or is not
     * anonymous.
     */
    public static boolean isLinked(ParseUser user) {
        return user.isLinked(AUTH_TYPE);
    }

    /**
     * Creates an anonymous user in the background.
     *
     * @return A Task that will be resolved when logging in is completed.
     */
    public static Task<ParseUser> logInInBackground() {
        return ParseUser.logInWithInBackground(AUTH_TYPE, getAuthData());
    }

    /**
     * Creates an anonymous user in the background.
     *
     * @param callback The callback to execute when anonymous user creation is complete.
     */
    public static void logIn(LogInCallback callback) {
        ParseTaskUtils.callbackOnMainThreadAsync(logInInBackground(), callback);
    }

    /* package */
    static Map<String, String> getAuthData() {
        Map<String, String> authData = new HashMap<>();
        authData.put("id", UUID.randomUUID().toString());
        return authData;
    }
}

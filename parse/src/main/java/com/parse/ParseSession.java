/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.parse.boltsinternal.Continuation;
import com.parse.boltsinternal.Task;

/**
 * The {@code ParseSession} is a local representation of session data that can be saved
 * and retrieved from the Parse cloud.
 */
@ParseClassName("_Session")
public class ParseSession extends ParseObject {

    private static final String KEY_SESSION_TOKEN = "sessionToken";
    private static final String KEY_CREATED_WITH = "createdWith";
    private static final String KEY_RESTRICTED = "restricted";
    private static final String KEY_USER = "user";
    private static final String KEY_EXPIRES_AT = "expiresAt";
    private static final String KEY_INSTALLATION_ID = "installationId";

    private static final List<String> READ_ONLY_KEYS = Collections.unmodifiableList(
            Arrays.asList(KEY_SESSION_TOKEN, KEY_CREATED_WITH, KEY_RESTRICTED, KEY_USER, KEY_EXPIRES_AT,
                    KEY_INSTALLATION_ID));

    private static ParseSessionController getSessionController() {
        return ParseCorePlugins.getInstance().getSessionController();
    }

    /**
     * Get the current {@code ParseSession} object related to the current user.
     *
     * @return A task that resolves a {@code ParseSession} object or {@code null} if not valid or
     * logged in.
     */
    public static Task<ParseSession> getCurrentSessionInBackground() {
        return ParseUser.getCurrentSessionTokenAsync().onSuccessTask(new Continuation<String, Task<ParseSession>>() {
            @Override
            public Task<ParseSession> then(Task<String> task) {
                String sessionToken = task.getResult();
                if (sessionToken == null) {
                    return Task.forResult(null);
                }
                return getSessionController().getSessionAsync(sessionToken).onSuccess(new Continuation<ParseObject.State, ParseSession>() {
                    @Override
                    public ParseSession then(Task<ParseObject.State> task) {
                        ParseObject.State result = task.getResult();
                        return ParseObject.from(result);
                    }
                });
            }
        });
    }

    /**
     * Get the current {@code ParseSession} object related to the current user.
     *
     * @param callback A callback that returns a {@code ParseSession} object or {@code null} if not
     *                 valid or logged in.
     */
    public static void getCurrentSessionInBackground(GetCallback<ParseSession> callback) {
        ParseTaskUtils.callbackOnMainThreadAsync(getCurrentSessionInBackground(), callback);
    }

    /* package */
    static Task<Void> revokeAsync(String sessionToken) {
        if (sessionToken == null || !isRevocableSessionToken(sessionToken)) {
            return Task.forResult(null);
        }
        return getSessionController().revokeAsync(sessionToken);
    }

    /* package */
    static Task<String> upgradeToRevocableSessionAsync(String sessionToken) {
        if (sessionToken == null || isRevocableSessionToken(sessionToken)) {
            return Task.forResult(sessionToken);
        }

        return getSessionController().upgradeToRevocable(sessionToken).onSuccess(new Continuation<ParseObject.State, String>() {
            @Override
            public String then(Task<ParseObject.State> task) {
                ParseObject.State result = task.getResult();
                return ParseObject.<ParseSession>from(result).getSessionToken();
            }
        });
    }

    /* package */
    static boolean isRevocableSessionToken(String sessionToken) {
        return sessionToken.contains("r:");
    }

    /**
     * Constructs a query for {@code ParseSession}.
     *
     * @see com.parse.ParseQuery#getQuery(Class)
     */
    public static ParseQuery<ParseSession> getQuery() {
        return ParseQuery.getQuery(ParseSession.class);
    }

    @Override
        /* package */ boolean needsDefaultACL() {
        return false;
    }

    @Override
        /* package */ boolean isKeyMutable(String key) {
        return !READ_ONLY_KEYS.contains(key);
    }

    /**
     * @return the session token for a user, if they are logged in.
     */
    public String getSessionToken() {
        return getString(KEY_SESSION_TOKEN);
    }
}

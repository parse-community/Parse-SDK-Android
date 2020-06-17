/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse.twitter;

import android.content.Context;

import com.parse.ParseException;

import java.util.HashMap;
import java.util.Map;

import com.parse.boltsinternal.Task;

class TwitterController {

    private static final String CONSUMER_KEY_KEY = "consumer_key";
    private static final String CONSUMER_SECRET_KEY = "consumer_secret";

    private static final String ID_KEY = "id";
    private static final String SCREEN_NAME_KEY = "screen_name";
    private static final String AUTH_TOKEN_KEY = "auth_token";
    private static final String AUTH_TOKEN_SECRET_KEY = "auth_token_secret";

    private final Twitter twitter;
    private Task<Map<String, String>>.TaskCompletionSource currentTcs;

    TwitterController() {
        this(new Twitter("", "", ""));
    }

    TwitterController(Twitter twitter) {
        this.twitter = twitter;
    }

    public void initialize(String consumerKey, String consumerSecret) {
        twitter.setConsumerKey(consumerKey).setConsumerSecret(consumerSecret);
    }

    public Twitter getTwitter() {
        return twitter;
    }

    public Task<Map<String, String>> authenticateAsync(Context context) {
        final Task<Map<String, String>>.TaskCompletionSource tcs = Task.create();
        if (currentTcs != null) {
            handleCancel(currentTcs);
        }
        currentTcs = tcs;

        if (context == null) {
            throw new IllegalStateException(
                    "Context must be non-null for Twitter authentication to proceed.");
        }
        twitter.authorize(context, new AsyncCallback() {
            @Override
            public void onCancel() {
                handleCancel(tcs);
            }

            @Override
            public void onFailure(Throwable error) {
                if (currentTcs != tcs) {
                    return;
                }
                try {
                    if (error instanceof Exception) {
                        tcs.trySetError((Exception) error);
                    } else {
                        tcs.trySetError(new ParseException(error));
                    }
                } finally {
                    currentTcs = null;
                }
            }

            @Override
            public void onSuccess(Object result) {
                if (currentTcs != tcs) {
                    return;
                }
                try {
                    Map<String, String> authData = getAuthData(
                            twitter.getUserId(),
                            twitter.getScreenName(),
                            twitter.getAuthToken(),
                            twitter.getAuthTokenSecret());
                    tcs.trySetResult(authData);
                } finally {
                    currentTcs = null;
                }
            }
        });

        return tcs.getTask();
    }

    private void handleCancel(Task<Map<String, String>>.TaskCompletionSource callback) {
        // Ensure that the operation being cancelled is actually the current
        // operation (so that if one had already been cancelled but still
        // invokes this method, it doesn't cancel the "real" current operation).
        if (currentTcs != callback || callback == null) {
            return;
        }
        try {
            callback.trySetCancelled();
        } finally {
            currentTcs = null;
        }
    }

    public Map<String, String> getAuthData(
            String userId,
            String screenName,
            String authToken,
            String authTokenSecret) {
        Map<String, String> authData = new HashMap<>();
        authData.put(CONSUMER_KEY_KEY, twitter.getConsumerKey());
        authData.put(CONSUMER_SECRET_KEY, twitter.getConsumerSecret());
        authData.put(ID_KEY, userId);
        authData.put(SCREEN_NAME_KEY, screenName);
        authData.put(AUTH_TOKEN_KEY, authToken);
        authData.put(AUTH_TOKEN_SECRET_KEY, authTokenSecret);
        return authData;
    }

    public void setAuthData(Map<String, String> authData) {
        if (authData == null) {
            twitter.setAuthToken(null);
            twitter.setAuthTokenSecret(null);
            twitter.setScreenName(null);
            twitter.setUserId(null);
            // Consumer key/secret are set by the application.
        } else {
            twitter.setAuthToken(authData.get(AUTH_TOKEN_KEY));
            twitter.setAuthTokenSecret(authData.get(AUTH_TOKEN_SECRET_KEY));
            twitter.setUserId(authData.get(ID_KEY));
            twitter.setScreenName(authData.get(SCREEN_NAME_KEY));
            // Consumer key/secret are set by the application.
        }
    }
}

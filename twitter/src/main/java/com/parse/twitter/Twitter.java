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
import android.net.Uri;
import android.os.AsyncTask;
import android.webkit.CookieSyncManager;

import com.parse.twitter.OAuth1FlowDialog.FlowResultHandler;

import oauth.signpost.http.HttpParameters;
import se.akerfeldt.okhttp.signpost.OkHttpOAuthConsumer;
import se.akerfeldt.okhttp.signpost.OkHttpOAuthProvider;

/**
 * Provides access to Twitter info as it relates to Parse
 */
public class Twitter {

    private static final String REQUEST_TOKEN_URL = "https://api.twitter.com/oauth/request_token";
    private static final String AUTHORIZE_URL = "https://api.twitter.com/oauth/authenticate";
    private static final String ACCESS_TOKEN_URL = "https://api.twitter.com/oauth/access_token";

    private static final String VERIFIER_PARAM = "oauth_verifier";
    private static final String USER_ID_PARAM = "user_id";
    private static final String SCREEN_NAME_PARAM = "screen_name";

    // App configuration for enabling authentication.
    private String consumerKey;
    private String consumerSecret;
    private String callbackUrl;

    // User information.
    private String authToken;
    private String authTokenSecret;
    private String userId;
    private String screenName;

    Twitter(String consumerKey, String consumerSecret, String callbackUrl) {
        this.consumerKey = consumerKey;
        this.consumerSecret = consumerSecret;
        this.callbackUrl = callbackUrl;
    }

    public String getConsumerKey() {
        return consumerKey;
    }

    public Twitter setConsumerKey(String consumerKey) {
        this.consumerKey = consumerKey;
        return this;
    }

    public String getConsumerSecret() {
        return consumerSecret;
    }

    public Twitter setConsumerSecret(String consumerSecret) {
        this.consumerSecret = consumerSecret;
        return this;
    }

    public String getAuthToken() {
        return authToken;
    }

    public void setAuthToken(String authToken) {
        this.authToken = authToken;
    }

    public String getAuthTokenSecret() {
        return authTokenSecret;
    }

    public void setAuthTokenSecret(String authTokenSecret) {
        this.authTokenSecret = authTokenSecret;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getScreenName() {
        return screenName;
    }

    public void setScreenName(String screenName) {
        this.screenName = screenName;
    }

    public void authorize(final Context context, final AsyncCallback callback) {
        if (getConsumerKey() == null || getConsumerKey().length() == 0 || getConsumerSecret() == null
                || getConsumerSecret().length() == 0) {
            throw new IllegalStateException(
                    "Twitter must be initialized with a consumer key and secret before authorization.");
        }

        final OkHttpOAuthProvider provider = new OkHttpOAuthProvider(REQUEST_TOKEN_URL, ACCESS_TOKEN_URL, AUTHORIZE_URL);
        final OkHttpOAuthConsumer consumer = new OkHttpOAuthConsumer(getConsumerKey(), getConsumerSecret());

        AsyncTask<Void, Void, String> task = new AsyncTask<Void, Void, String>() {
            private Throwable error;

            @Override
            protected String doInBackground(Void... params) {
                try {
                    return provider.retrieveRequestToken(consumer, callbackUrl);
                } catch (Throwable e) {
                    error = e;
                }
                return null;
            }

            @Override
            protected void onPostExecute(String result) {
                super.onPostExecute(result);
                if (error != null) {
                    callback.onFailure(error);
                    return;
                }
                CookieSyncManager.createInstance(context);
                OAuth1FlowDialog dialog = new OAuth1FlowDialog(context, result, callbackUrl,
                        "api.twitter", new FlowResultHandler() {

                    @Override
                    public void onError(int errorCode, String description, String failingUrl) {
                        callback.onFailure(new OAuth1FlowException(errorCode, description, failingUrl));
                    }

                    @Override
                    public void onComplete(String callbackUrl) {
                        CookieSyncManager.getInstance().sync();
                        Uri uri = Uri.parse(callbackUrl);
                        final String verifier = uri.getQueryParameter(VERIFIER_PARAM);
                        if (verifier == null) {
                            callback.onCancel();
                            return;
                        }
                        AsyncTask<Void, Void, HttpParameters> getTokenTask = new AsyncTask<Void, Void, HttpParameters>() {
                            private Throwable error;

                            @Override
                            protected HttpParameters doInBackground(Void... params) {
                                try {
                                    provider.retrieveAccessToken(consumer, verifier);
                                } catch (Throwable e) {
                                    error = e;
                                }
                                return provider.getResponseParameters();
                            }

                            @Override
                            protected void onPostExecute(HttpParameters result) {
                                super.onPostExecute(result);
                                if (error != null) {
                                    callback.onFailure(error);
                                    return;
                                }
                                try {
                                    setAuthToken(consumer.getToken());
                                    setAuthTokenSecret(consumer.getTokenSecret());
                                    setScreenName(result.getFirst(SCREEN_NAME_PARAM));
                                    setUserId(result.getFirst(USER_ID_PARAM));
                                } catch (Throwable e) {
                                    callback.onFailure(e);
                                    return;
                                }
                                callback.onSuccess(Twitter.this);
                            }
                        };
                        getTokenTask.execute();
                    }

                    @Override
                    public void onCancel() {
                        callback.onCancel();
                    }
                });
                dialog.show();
            }
        };
        task.execute();
    }

}

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

import com.parse.AuthenticationCallback;
import com.parse.LogInCallback;
import com.parse.ParseException;
import com.parse.ParseUser;
import com.parse.SaveCallback;

import java.util.Map;
import java.util.concurrent.CancellationException;

import com.parse.boltsinternal.AggregateException;
import com.parse.boltsinternal.Continuation;
import com.parse.boltsinternal.Task;

/**
 * Provides a set of utilities for using Parse with Twitter.
 */
public final class ParseTwitterUtils {
    private static final String AUTH_TYPE = "twitter";

    private static final String CALLBACK_URL = "twittersdk://";

    private static final Object lock = new Object();
    static boolean isInitialized;
    static TwitterController controller;
    static ParseUserDelegate userDelegate = new ParseUserDelegateImpl();

    private static TwitterController getTwitterController() {
        synchronized (lock) {
            if (controller == null) {
                controller = new TwitterController();
            }
            return controller;
        }
    }

    /**
     * Gets the shared {@link Twitter} singleton that Parse is using.
     *
     * @return {@link Twitter} instance.
     */
    public static Twitter getTwitter() {
        return getTwitterController().getTwitter();
    }

    /**
     * Initializes Twitter for use with Parse. This method must be invoked prior to calling
     * {@link #link(ParseUser, Context, SaveCallback)} and {@link #logIn(Context, LogInCallback)}.
     *
     * @param consumerKey    Your Twitter consumer key.
     * @param consumerSecret Your Twitter consumer secret.
     */
    public static void initialize(String consumerKey, String consumerSecret) {
        initialize(consumerKey, consumerSecret, CALLBACK_URL);
    }

    /**
     * Initializes Twitter for use with Parse. This method must be invoked prior to calling
     * {@link #link(ParseUser, Context, SaveCallback)} and {@link #logIn(Context, LogInCallback)}.
     *
     * @param consumerKey    Your Twitter consumer key.
     * @param consumerSecret Your Twitter consumer secret.
     * @param callbackUrl    the callback url
     */
    public static void initialize(String consumerKey, String consumerSecret, String callbackUrl) {
        synchronized (lock) {
            if (isInitialized) {
                return;
            }

            if (controller == null) {
                Twitter twitter = new Twitter(consumerKey, consumerSecret, callbackUrl);
                controller = new TwitterController(twitter);
            } else {
                controller.initialize(consumerKey, consumerSecret);
            }

            userDelegate.registerAuthenticationCallback(AUTH_TYPE, new AuthenticationCallback() {
                @Override
                public boolean onRestore(Map<String, String> authData) {
                    try {
                        getTwitterController().setAuthData(authData);
                        return true;
                    } catch (Exception e) {
                        return false;
                    }
                }
            });

            isInitialized = true;
        }
    }

    private static void checkInitialization() {
        if (!isInitialized) {
            throw new IllegalStateException(
                    "You must call ParseTwitterUtils.initialize() before using ParseTwitterUtils");
        }
    }

    /**
     * @return {@code true} if the user is linked to a Twitter account.
     */
    public static boolean isLinked(ParseUser user) {
        return user.isLinked(AUTH_TYPE);
    }

    /**
     * Links a ParseUser to a Twitter account, allowing you to use Twitter for authentication, and
     * providing access to Twitter data for the user. A dialog will be shown to the user for Twitter
     * authentication.
     *
     * @param user    The user to link to a Twitter account.
     * @param context An Android context from which the login dialog can be launched.
     * @return A Task that will be resolved when linking is completed.
     */
    public static Task<Void> linkInBackground(Context context, final ParseUser user) {
        checkInitialization();
        return getTwitterController().authenticateAsync(context).onSuccessTask(new Continuation<Map<String, String>, Task<Void>>() {
            @Override
            public Task<Void> then(Task<Map<String, String>> task) {
                return user.linkWithInBackground(AUTH_TYPE, task.getResult());
            }
        });
    }

    /**
     * @deprecated Please use {@link ParseTwitterUtils#linkInBackground(Context, ParseUser)} instead.
     */
    @Deprecated
    public static void link(ParseUser user, Context context) {
        link(user, context, null);
    }

    /**
     * Links a ParseUser to a Twitter account, allowing you to use Twitter for authentication, and
     * providing access to Twitter data for the user. A dialog will be shown to the user for Twitter
     * authentication.
     *
     * @param user     The user to link to a Twitter account.
     * @param context  An Android context from which the login dialog can be launched.
     * @param callback Callback for notifying the calling application when the Twitter authentication has
     *                 completed, failed, or been canceled.
     * @see #linkInBackground(Context, ParseUser)
     */
    public static void link(ParseUser user, Context context, SaveCallback callback) {
        callbackOnMainThreadAsync(linkInBackground(context, user), callback, true);
    }

    /**
     * Links a ParseUser to a Twitter account, allowing you to use Twitter for authentication, and
     * providing access to Twitter data for the user. This method allows you to handle getting the
     * auth tokens for your users, rather than delegating to the provided dialog log-in.
     *
     * @param user            The user to link to a Twitter account.
     * @param twitterId       The user's Twitter ID.
     * @param screenName      The user's Twitter screen name.
     * @param authToken       The auth token for the session.
     * @param authTokenSecret The auth token secret for the session.
     * @return A Task that will be resolved when linking is completed.
     */
    public static Task<Void> linkInBackground(ParseUser user, String twitterId, String screenName,
                                              String authToken, String authTokenSecret) {
        checkInitialization();
        Map<String, String> authData = getTwitterController().getAuthData(
                twitterId,
                screenName,
                authToken,
                authTokenSecret);
        return user.linkWithInBackground(AUTH_TYPE, authData);
    }

    /**
     * @deprecated Please use {@link ParseTwitterUtils#linkInBackground(ParseUser, String, String,
     * String, String)} instead.
     */
    @Deprecated
    public static void link(ParseUser user, String twitterId, String screenName, String authToken,
                            String authTokenSecret) {
        link(user, twitterId, screenName, authToken, authTokenSecret, null);
    }

    /**
     * Links a ParseUser to a Twitter account, allowing you to use Twitter for authentication, and
     * providing access to Twitter data for the user. This method allows you to handle getting the
     * auth tokens for your users, rather than delegating to the provided dialog log-in.
     *
     * @param user            The user to link to a Twitter account.
     * @param twitterId       The user's Twitter ID.
     * @param screenName      The user's Twitter screen name.
     * @param authToken       The auth token for the session.
     * @param authTokenSecret The auth token secret for the session.
     * @param callback        Callback for notifying that the authentication data has been saved to the ParseUser.
     * @see #linkInBackground(ParseUser, String, String, String, String)
     */
    public static void link(ParseUser user, String twitterId, String screenName, String authToken,
                            String authTokenSecret, SaveCallback callback) {
        callbackOnMainThreadAsync(
                linkInBackground(user, twitterId, screenName, authToken, authTokenSecret),
                callback,
                false
        );
    }

    /**
     * Logs in a ParseUser using Twitter for authentication. If a user for the given Twitter
     * credentials does not already exist, a new user will be created. This method allows you to
     * handle getting the auth tokens for your users, rather than delegating to the provided dialog
     * log-in.
     *
     * @param twitterId       The user's Twitter ID.
     * @param screenName      The user's Twitter screen name.
     * @param authToken       The auth token for the session.
     * @param authTokenSecret The auth token secret for the session.
     * @return A Task that will be resolved when logging in is completed.
     */
    public static Task<ParseUser> logInInBackground(String twitterId, String screenName,
                                                    String authToken, String authTokenSecret) {
        checkInitialization();
        Map<String, String> authData = getTwitterController().getAuthData(
                twitterId,
                screenName,
                authToken,
                authTokenSecret);
        return userDelegate.logInWithInBackground(AUTH_TYPE, authData);
    }

    /**
     * Logs in a ParseUser using Twitter for authentication. If a user for the given Twitter
     * credentials does not already exist, a new user will be created. This method allows you to
     * handle getting the auth tokens for your users, rather than delegating to the provided dialog
     * log-in.
     *
     * @param twitterId       The user's Twitter ID.
     * @param screenName      The user's Twitter screen name.
     * @param authToken       The auth token for the session.
     * @param authTokenSecret The auth token secret for the session.
     * @param callback        Callback for notifying that the authentication data has been saved to the ParseUser.
     * @see #logInInBackground(String, String, String, String)
     */
    public static void logIn(String twitterId, String screenName, String authToken,
                             String authTokenSecret, LogInCallback callback) {
        callbackOnMainThreadAsync(
                logInInBackground(twitterId, screenName, authToken, authTokenSecret),
                callback,
                false
        );
    }

    /**
     * Logs in a ParseUser using Twitter for authentication. If a user for the given Twitter
     * credentials does not already exist, a new user will be created. A dialog will be shown to the
     * user for Twitter authentication.
     *
     * @param context An Android context from which the login dialog can be launched.
     * @return A Task that will be resolved when logging in is completed.
     */
    public static Task<ParseUser> logInInBackground(Context context) {
        checkInitialization();
        return getTwitterController().authenticateAsync(context).onSuccessTask(new Continuation<Map<String, String>, Task<ParseUser>>() {
            @Override
            public Task<ParseUser> then(Task<Map<String, String>> task) {
                return userDelegate.logInWithInBackground(AUTH_TYPE, task.getResult());
            }
        });
    }

    /**
     * Logs in a ParseUser using Twitter for authentication. If a user for the given Twitter
     * credentials does not already exist, a new user will be created. A dialog will be shown to the
     * user for Twitter authentication.
     *
     * @param context  An Android context from which the login dialog can be launched.
     * @param callback Callback for notifying the calling application when the Twitter authentication has
     *                 completed, failed, or been canceled.
     * @see #logInInBackground(android.content.Context)
     */
    public static void logIn(Context context, LogInCallback callback) {
        callbackOnMainThreadAsync(logInInBackground(context), callback, true);
    }

    /**
     * Unlinks a user from a Twitter account. Unlinking a user will save the user's data.
     *
     * @param user The user to unlink from a Facebook account.
     */
    public static void unlink(ParseUser user) throws ParseException {
        wait(unlinkInBackground(user));
    }

    /**
     * Unlinks a user from a Twitter account in the background. Unlinking a user will save the user's
     * data.
     *
     * @param user The user to unlink from a Facebook account.
     * @return A Task that will be resolved when unlinking is completed.
     */
    public static Task<Void> unlinkInBackground(ParseUser user) {
        checkInitialization();
        return user.unlinkFromInBackground(AUTH_TYPE);
    }

    /**
     * Unlinks a user from a Twitter account in the background. Unlinking a user will save the user's
     * data.
     *
     * @param user     The user to unlink from a Facebook account.
     * @param callback Callback for notifying when unlinking is complete.
     * @see #unlinkInBackground(ParseUser)
     */
    public static void unlinkInBackground(ParseUser user, SaveCallback callback) {
        callbackOnMainThreadAsync(unlinkInBackground(user), callback, false);
    }

    //region TaskUtils

    /**
     * Converts a task execution into a synchronous action.
     */
    //TODO (grantland): Task.cs actually throws an AggregateException if the task was cancelled with
    // TaskCancellationException as an inner exception or an AggregateException with the original
    // exception as an inner exception if task.isFaulted().
    // https://msdn.microsoft.com/en-us/library/dd235635(v=vs.110).aspx
    /* package */
    static <T> T wait(Task<T> task) throws ParseException {
        try {
            task.waitForCompletion();
            if (task.isFaulted()) {
                Exception error = task.getError();
                if (error instanceof ParseException) {
                    throw (ParseException) error;
                }
                if (error instanceof AggregateException) {
                    throw new ParseException(error);
                }
                if (error instanceof RuntimeException) {
                    throw (RuntimeException) error;
                }
                throw new RuntimeException(error);
            } else if (task.isCancelled()) {
                throw new RuntimeException(new CancellationException());
            }
            return task.getResult();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Calls the callback after a task completes on the main thread, returning a Task that completes
     * with the same result as the input task after the callback has been run.
     */
    private static <T> Task<T> callbackOnMainThreadAsync(
            Task<T> task, LogInCallback callback, boolean reportCancellation) {
        return callbackOnMainThreadInternalAsync(task, callback, reportCancellation);
    }

    /**
     * Calls the callback after a task completes on the main thread, returning a Task that completes
     * with the same result as the input task after the callback has been run.
     */
    private static <T> Task<T> callbackOnMainThreadAsync(
            Task<T> task, SaveCallback callback, boolean reportCancellation) {
        return callbackOnMainThreadInternalAsync(task, callback, reportCancellation);
    }

    /**
     * Calls the callback after a task completes on the main thread, returning a Task that completes
     * with the same result as the input task after the callback has been run. If reportCancellation
     * is false, the callback will not be called if the task was cancelled.
     */
    private static <T> Task<T> callbackOnMainThreadInternalAsync(
            Task<T> task, final Object callback, final boolean reportCancellation) {
        if (callback == null) {
            return task;
        }
        final Task<T>.TaskCompletionSource tcs = Task.create();
        task.continueWith(new Continuation<T, Void>() {
            @Override
            public Void then(final Task<T> task) throws Exception {
                if (task.isCancelled() && !reportCancellation) {
                    tcs.setCancelled();
                    return null;
                }
                Task.UI_THREAD_EXECUTOR.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Exception error = task.getError();
                            if (error != null && !(error instanceof ParseException)) {
                                error = new ParseException(error);
                            }
                            if (callback instanceof SaveCallback) {
                                ((SaveCallback) callback).done((ParseException) error);
                            } else if (callback instanceof LogInCallback) {
                                ((LogInCallback) callback).done(
                                        (ParseUser) task.getResult(), (ParseException) error);
                            }
                        } finally {
                            if (task.isCancelled()) {
                                tcs.setCancelled();
                            } else if (task.isFaulted()) {
                                tcs.setError(task.getError());
                            } else {
                                tcs.setResult(task.getResult());
                            }
                        }
                    }
                });
                return null;
            }
        });
        return tcs.getTask();
    }

    //endregion

    private ParseTwitterUtils() {
        // do nothing
    }

    interface ParseUserDelegate {
        void registerAuthenticationCallback(String authType, AuthenticationCallback callback);

        Task<ParseUser> logInWithInBackground(String authType, Map<String, String> authData);
    }

    private static class ParseUserDelegateImpl implements ParseUserDelegate {
        @Override
        public void registerAuthenticationCallback(String authType, AuthenticationCallback callback) {
            ParseUser.registerAuthenticationCallback(authType, callback);
        }

        @Override
        public Task<ParseUser> logInWithInBackground(String authType, Map<String, String> authData) {
            return ParseUser.logInWithInBackground(authType, authData);
        }
    }
}

/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse.facebook;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import androidx.fragment.app.Fragment;

import com.facebook.AccessToken;
import com.parse.AuthenticationCallback;
import com.parse.LogInCallback;
import com.parse.ParseException;
import com.parse.ParseUser;
import com.parse.SaveCallback;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import com.parse.boltsinternal.Continuation;
import com.parse.boltsinternal.Task;

/**
 * Provides a set of utilities for using Parse with Facebook.
 * <p>
 * <strong>Note:</strong> {@code ParseFacebookUtils} requires Facebook Android SDK v4.x.x
 * <p>
 * To use {@code ParseFacebookUtils}, you'll need to set up the Facebook SDK:
 * <p>
 * Add the Facebook SDK: {@code compile 'com.facebook.android:facebook-android-sdk:4.x.x'}
 * <p>
 * Add the following to the {@code <application>} node in your AndroidManifest.xml:
 * <pre>
 * &lt;meta-data
 *        android:name="com.facebook.sdk.ApplicationId"
 *        android:value="@string/facebook_app_id"/&gt;
 * </pre>
 * <p>
 * Create {@code facebook_app_id} in your strings.xml with your Facebook App ID
 * <p>
 * Then you can use {@code ParseFacebookUtils}:
 * <p>
 * Initialize {@code ParseFacebookUtils} in your {@link android.app.Application#onCreate}:
 * <pre>
 * public class MyApplication extends Application {
 *   public void onCreate() {
 *     Parse.initialize(this);
 *     ...
 *     ParseFacebookUtils.initialize(this);
 *   }
 * }
 * </pre>
 * <p>
 * Add {@link ParseFacebookUtils#onActivityResult(int, int, android.content.Intent)} to
 * your {@link android.app.Activity#onActivityResult(int, int, android.content.Intent)}:
 * <pre>
 * public class MyActivity extends Activity {
 *   protected void onActivityResult(int requestCode, int resultCode, Intent data) {
 *     super.onActivityResult(requestCode, resultCode, data);
 *     ParseFacebookUtils.onActivityResult(requestCode, resultCode, data);
 *   }
 * }
 * </pre>
 * <p>
 * Lastly, log in with {@link ParseFacebookUtils#logInWithReadPermissionsInBackground(android.app.Activity, java.util.Collection)}
 */
public final class ParseFacebookUtils {
    private static final String AUTH_TYPE = "facebook";

    private static final Object lock = new Object();
    static boolean isInitialized;
    static FacebookController controller;
    static ParseUserDelegate userDelegate = new ParseUserDelegateImpl();

    /**
     * @param user A {@link com.parse.ParseUser} object.
     * @return {@code true} if the user is linked to a Facebook account.
     */
    public static boolean isLinked(ParseUser user) {
        return user.isLinked(AUTH_TYPE);
    }

    /**
     * Initializes {@code ParseFacebookUtils} and {@link com.facebook.FacebookSdk}.
     * <p>
     * This should be called in your {@link android.app.Application#onCreate()}.
     *
     * @param context The application context
     */
    public static void initialize(Context context) {
        initialize(context, FacebookController.DEFAULT_AUTH_ACTIVITY_CODE);
    }

    /**
     * Initializes {@code ParseFacebookUtils} and {@link com.facebook.FacebookSdk}.
     * <p>
     * This should be called in your {@link android.app.Application#onCreate()}.
     *
     * @param context                   The application context
     * @param callbackRequestCodeOffset The request code offset that Facebook activities will be
     *                                  called with. Please do not use the range between the
     *                                  value you set and another 100 entries after it in your
     *                                  other requests.
     */
    public static void initialize(Context context, int callbackRequestCodeOffset) {
        synchronized (lock) {
            getController().initialize(context, callbackRequestCodeOffset);
            userDelegate.registerAuthenticationCallback(AUTH_TYPE, new AuthenticationCallback() {
                @Override
                public boolean onRestore(Map<String, String> authData) {
                    try {
                        getController().setAuthData(authData);
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
        synchronized (lock) {
            if (!isInitialized) {
                throw new IllegalStateException(
                        "You must call ParseFacebookUtils.initialize() before using ParseFacebookUtils");
            }
        }
    }

    private static FacebookController getController() {
        synchronized (lock) {
            if (controller == null) {
                controller = new FacebookController();
            }
            return controller;
        }
    }

    /**
     * The method that should be called from the Activity's or Fragment's onActivityResult method.
     *
     * @param requestCode The request code that's received by the Activity or Fragment.
     * @param resultCode  The result code that's received by the Activity or Fragment.
     * @param data        The result data that's received by the Activity or Fragment.
     * @return {@code true} if the result could be handled.
     */
    public static boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        synchronized (lock) {
            if (controller != null) {
                return controller.onActivityResult(requestCode, resultCode, data);
            }
            return false;
        }
    }

    //region Log In

    /**
     * Log in using a Facebook account using authorization credentials that have already been
     * obtained.
     *
     * @param accessToken Authorization credentials of a Facebook user.
     * @return A task that will be resolved when logging in is complete.
     */
    public static Task<ParseUser> logInInBackground(AccessToken accessToken) {
        checkInitialization();
        return userDelegate.logInWithInBackground(AUTH_TYPE, getController().getAuthData(accessToken));
    }

    /**
     * Log in using a Facebook account using authorization credentials that have already been
     * obtained.
     *
     * @param accessToken Authorization credentials of a Facebook user.
     * @param callback    A callback that will be executed when logging in is complete.
     * @return A task that will be resolved when logging in is complete.
     */
    public static Task<ParseUser> logInInBackground(AccessToken accessToken, LogInCallback callback) {
        return callbackOnMainThreadAsync(logInInBackground(accessToken), callback, true);
    }

    /**
     * Log in using Facebook login with the requested read permissions.
     *
     * @param activity    The activity which is starting the login process.
     * @param permissions The requested permissions.
     * @return A task that will be resolved when logging in is complete.
     */
    public static Task<ParseUser> logInWithReadPermissionsInBackground(Activity activity,
                                                                       Collection<String> permissions) {
        return logInAsync(activity, null, permissions, FacebookController.LoginAuthorizationType.READ);
    }

    /**
     * Log in using Facebook login with the requested read permissions.
     *
     * @param activity    The activity which is starting the login process.
     * @param permissions The requested permissions.
     * @param callback    A callback that will be executed when logging in is complete.
     * @return A task that will be resolved when logging in is complete.
     */
    public static Task<ParseUser> logInWithReadPermissionsInBackground(Activity activity,
                                                                       Collection<String> permissions, LogInCallback callback) {
        return callbackOnMainThreadAsync(
                logInWithReadPermissionsInBackground(activity, permissions), callback, true);
    }

    /**
     * Log in using Facebook login with the requested publish permissions.
     *
     * @param activity    The activity which is starting the login process.
     * @param permissions The requested permissions.
     * @return A task that will be resolved when logging in is complete.
     */
    public static Task<ParseUser> logInWithPublishPermissionsInBackground(Activity activity,
                                                                          Collection<String> permissions) {
        return logInAsync(activity, null, permissions, FacebookController.LoginAuthorizationType.PUBLISH);
    }

    /**
     * Log in using Facebook login with the requested publish permissions.
     *
     * @param activity    The activity which is starting the login process.
     * @param permissions The requested permissions.
     * @param callback    A callback that will be executed when logging in is complete.
     * @return A task that will be resolved when logging in is complete.
     */
    public static Task<ParseUser> logInWithPublishPermissionsInBackground(Activity activity,
                                                                          Collection<String> permissions, LogInCallback callback) {
        return callbackOnMainThreadAsync(
                logInWithPublishPermissionsInBackground(activity, permissions), callback, true);
    }

    /**
     * Log in using Facebook login with the requested read permissions.
     *
     * @param fragment    The fragment which is starting the login process.
     * @param permissions The requested permissions.
     * @return A task that will be resolved when logging in is complete.
     */
    public static Task<ParseUser> logInWithReadPermissionsInBackground(Fragment fragment,
                                                                       Collection<String> permissions) {
        return logInAsync(null, fragment, permissions, FacebookController.LoginAuthorizationType.READ);
    }

    /**
     * Log in using Facebook login with the requested read permissions.
     *
     * @param fragment    The fragment which is starting the login process.
     * @param permissions The requested permissions.
     * @param callback    A callback that will be executed when logging in is complete.
     * @return A task that will be resolved when logging in is complete.
     */
    public static Task<ParseUser> logInWithReadPermissionsInBackground(Fragment fragment,
                                                                       Collection<String> permissions, LogInCallback callback) {
        return callbackOnMainThreadAsync(
                logInWithReadPermissionsInBackground(fragment, permissions), callback, true);
    }

    /**
     * Log in using Facebook login with the requested publish permissions.
     *
     * @param fragment    The fragment which is starting the login process.
     * @param permissions The requested permissions.
     * @return A task that will be resolved when logging in is complete.
     */
    public static Task<ParseUser> logInWithPublishPermissionsInBackground(Fragment fragment,
                                                                          Collection<String> permissions) {
        return logInAsync(null, fragment, permissions, FacebookController.LoginAuthorizationType.PUBLISH);
    }

    /**
     * Log in using Facebook login with the requested publish permissions.
     *
     * @param fragment    The fragment which is starting the login process.
     * @param permissions The requested permissions.
     * @param callback    A callback that will be executed when logging in is complete.
     * @return A task that will be resolved when logging in is complete.
     */
    public static Task<ParseUser> logInWithPublishPermissionsInBackground(Fragment fragment,
                                                                          Collection<String> permissions, LogInCallback callback) {
        return callbackOnMainThreadAsync(
                logInWithPublishPermissionsInBackground(fragment, permissions), callback, true);
    }

    private static Task<ParseUser> logInAsync(Activity activity, Fragment fragment,
                                              Collection<String> permissions, FacebookController.LoginAuthorizationType authorizationType) {
        checkInitialization();
        if (permissions == null) {
            permissions = Collections.emptyList();
        }

        return getController().authenticateAsync(
                activity, fragment, authorizationType, permissions).onSuccessTask(new Continuation<Map<String, String>, Task<ParseUser>>() {
            @Override
            public Task<ParseUser> then(Task<Map<String, String>> task) throws Exception {
                return userDelegate.logInWithInBackground(AUTH_TYPE, task.getResult());
            }
        });
    }

    //endregion

    //region Link

    /**
     * Link an existing Parse user with a Facebook account using authorization credentials that have
     * already been obtained.
     *
     * @param user        The Parse user to link with.
     * @param accessToken Authorization credentials of a Facebook user.
     * @return A task that will be resolved when linking is complete.
     */
    public static Task<Void> linkInBackground(ParseUser user, AccessToken accessToken) {
        checkInitialization();
        return user.linkWithInBackground(AUTH_TYPE, getController().getAuthData(accessToken));
    }

    /**
     * Link an existing Parse user with a Facebook account using authorization credentials that have
     * already been obtained.
     *
     * @param user        The Parse user to link with.
     * @param accessToken Authorization credentials of a Facebook user.
     * @param callback    A callback that will be executed when linking is complete.
     * @return A task that will be resolved when logging in is complete.
     */
    public static Task<Void> linkInBackground(ParseUser user, AccessToken accessToken,
                                              SaveCallback callback) {
        return callbackOnMainThreadAsync(linkInBackground(user, accessToken), callback, true);
    }

    /**
     * Link an existing Parse user to Facebook with the requested read permissions.
     *
     * @param user        The Parse user to link with.
     * @param activity    The activity which is starting the login process.
     * @param permissions The requested permissions.
     * @return A task that will be resolved when linking is complete.
     */
    public static Task<Void> linkWithReadPermissionsInBackground(ParseUser user,
                                                                 Activity activity, Collection<String> permissions) {
        return linkAsync(user, activity, null, permissions, FacebookController.LoginAuthorizationType.READ);
    }

    /**
     * Link an existing Parse user to Facebook with the requested read permissions.
     *
     * @param user        The Parse user to link with.
     * @param activity    The activity which is starting the login process.
     * @param permissions The requested permissions.
     * @param callback    A callback that will be executed when linking is complete.
     * @return A task that will be resolved when linking is complete.
     */
    public static Task<Void> linkWithReadPermissionsInBackground(ParseUser user,
                                                                 Activity activity, Collection<String> permissions, SaveCallback callback) {
        return callbackOnMainThreadAsync(
                linkWithReadPermissionsInBackground(user, activity, permissions), callback, true);
    }

    /**
     * Link an existing Parse user to Facebook with the requested publish permissions.
     *
     * @param user        The Parse user to link with.
     * @param activity    The activity which is starting the login process.
     * @param permissions The requested permissions.
     * @return A task that will be resolved when linking is complete.
     */
    public static Task<Void> linkWithPublishPermissionsInBackground(ParseUser user,
                                                                    Activity activity, Collection<String> permissions) {
        return linkAsync(user, activity, null, permissions, FacebookController.LoginAuthorizationType.PUBLISH);
    }

    /**
     * Link an existing Parse user to Facebook with the requested publish permissions.
     *
     * @param user        The Parse user to link with.
     * @param activity    The activity which is starting the login process.
     * @param permissions The requested permissions.
     * @param callback    A callback that will be executed when linking is complete.
     * @return A task that will be resolved when linking is complete.
     */
    public static Task<Void> linkWithPublishPermissionsInBackground(ParseUser user,
                                                                    Activity activity, Collection<String> permissions, SaveCallback callback) {
        return callbackOnMainThreadAsync(
                linkWithPublishPermissionsInBackground(user, activity, permissions), callback, true);
    }

    /**
     * Link an existing Parse user to Facebook with the requested read permissions.
     *
     * @param user        The Parse user to link with.
     * @param fragment    The fragment which is starting the login process.
     * @param permissions The requested permissions.
     * @return A task that will be resolved when linking is complete.
     */
    public static Task<Void> linkWithReadPermissionsInBackground(ParseUser user,
                                                                 Fragment fragment, Collection<String> permissions) {
        return linkAsync(user, null, fragment, permissions, FacebookController.LoginAuthorizationType.READ);
    }

    /**
     * Link an existing Parse user to Facebook with the requested read permissions.
     *
     * @param user        The Parse user to link with.
     * @param fragment    The fragment which is starting the login process.
     * @param permissions The requested permissions.
     * @param callback    A callback that will be executed when linking is complete.
     * @return A task that will be resolved when linking is complete.
     */
    public static Task<Void> linkWithReadPermissionsInBackground(ParseUser user,
                                                                 Fragment fragment, Collection<String> permissions, SaveCallback callback) {
        return callbackOnMainThreadAsync(
                linkWithReadPermissionsInBackground(user, fragment, permissions), callback, true);
    }

    /**
     * Link an existing Parse user to Facebook with the requested publish permissions.
     *
     * @param user        The Parse user to link with.
     * @param fragment    The fragment which is starting the login process.
     * @param permissions The requested permissions.
     * @return A task that will be resolved when linking is complete.
     */
    public static Task<Void> linkWithPublishPermissionsInBackground(ParseUser user,
                                                                    Fragment fragment, Collection<String> permissions) {
        return linkAsync(user, null, fragment, permissions, FacebookController.LoginAuthorizationType.PUBLISH);
    }

    /**
     * Link an existing Parse user to Facebook with the requested publish permissions.
     *
     * @param user        The Parse user to link with.
     * @param fragment    The fragment which is starting the login process.
     * @param permissions The requested permissions.
     * @param callback    A callback that will be executed when linking is complete.
     * @return A task that will be resolved when linking is complete.
     */
    public static Task<Void> linkWithPublishPermissionsInBackground(ParseUser user,
                                                                    Fragment fragment, Collection<String> permissions, SaveCallback callback) {
        return callbackOnMainThreadAsync(
                linkWithPublishPermissionsInBackground(user, fragment, permissions), callback, true);
    }

    private static Task<Void> linkAsync(final ParseUser user, Activity activity, Fragment fragment,
                                        Collection<String> permissions, FacebookController.LoginAuthorizationType authorizationType) {
        checkInitialization();
        if (permissions == null) {
            permissions = Collections.emptyList();
        }

        return getController().authenticateAsync(
                activity, fragment, authorizationType, permissions).onSuccessTask(new Continuation<Map<String, String>, Task<Void>>() {
            @Override
            public Task<Void> then(Task<Map<String, String>> task) throws Exception {
                return user.linkWithInBackground(AUTH_TYPE, task.getResult());
            }
        });
    }

    //endregion

    //region Unlink

    /**
     * Unlink a user from a Facebook account. This will save the user's data.
     *
     * @param user The user to unlink.
     * @return A task that will be resolved when unlinking has completed.
     */
    public static Task<Void> unlinkInBackground(ParseUser user) {
        checkInitialization();
        return user.unlinkFromInBackground(AUTH_TYPE);
    }

    /**
     * Unlink a user from a Facebook account. This will save the user's data.
     *
     * @param user     The user to unlink.
     * @param callback A callback that will be executed when unlinking is complete.
     * @return A task that will be resolved when linking is complete.
     */
    public static Task<Void> unlinkInBackground(ParseUser user, SaveCallback callback) {
        return callbackOnMainThreadAsync(unlinkInBackground(user), callback, false);
    }

    //endregion

    //region TaskUtils

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

    private ParseFacebookUtils() {
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

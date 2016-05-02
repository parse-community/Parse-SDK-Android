package com.parse;

import android.content.Context;

import java.util.Map;

import bolts.Continuation;
import bolts.Task;
import bolts.Task.TaskCompletionSource;

public class ParseGoogleUtils {

    private static final String AUTH_TYPE = "google";
    private static final Object lock = new Object();

    private static boolean isInitialized;
    private static GoogleController controller;
    private static ParseUserDelegate userDelegate = new ParseUserDelegateImpl();

    public static void initialize(Context context) {
        initialize(context, '龜');
    }

    public static void initialize(Context context, int callbackRequestCodeOffset) {
        synchronized (lock) {
            getController().initialize(context, callbackRequestCodeOffset);
            userDelegate.registerAuthenticationCallback(AUTH_TYPE, new AuthenticationCallback() {
                public boolean onRestore(Map<String, String> authData) {
                    try {
                        getController().setAuthData(authData);
                        return true;
                    } catch (Exception var3) {
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
                throw new IllegalStateException("You must call ParseGoogleUtils.initialize() before using ParseGoogleUtils");
            }
        }
    }

    private static GoogleController getController() {
        synchronized (lock) {
            if (controller == null) {
                controller = new GoogleController();
            }

            return controller;
        }
    }

    public static Task<ParseUser> logInInBackground(String idToken) {
        checkInitialization();
        return userDelegate.logInWithInBackground(AUTH_TYPE, getController().getAuthData(idToken));
    }

    public static Task<ParseUser> logInInBackground(String idToken, LogInCallback callback) {
        return callbackOnMainThreadAsync(logInInBackground(idToken), callback, true);
    }

    private static <T> Task<T> callbackOnMainThreadAsync(Task<T> task, LogInCallback callback, boolean reportCancellation) {
        return callbackOnMainThreadInternalAsync(task, callback, reportCancellation);
    }

    private static <T> Task<T> callbackOnMainThreadInternalAsync(Task<T> task, final Object callback, final boolean reportCancellation) {
        if (callback == null) {
            return task;
        } else {
            final TaskCompletionSource tcs = Task.create();
            task.continueWith(new Continuation() {
                @Override
                public Object then(final Task task) throws Exception {
                    if (task.isCancelled() && !reportCancellation) {
                        tcs.setCancelled();
                        return null;
                    } else {
                        Task.UI_THREAD_EXECUTOR.execute(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    Object error = task.getError();
                                    if (error != null && !(error instanceof ParseException)) {
                                        error = new ParseException((Throwable) error);
                                    }

                                    if (callback instanceof SaveCallback) {
                                        ((SaveCallback) callback).done((ParseException) error);
                                    } else if (callback instanceof LogInCallback) {
                                        ((LogInCallback) callback).done((ParseUser) task.getResult(), (ParseException) error);
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
                }
            });
            return tcs.getTask();
        }
    }

    private static class ParseUserDelegateImpl implements ParseUserDelegate {
        private ParseUserDelegateImpl() {
        }

        public void registerAuthenticationCallback(String authType, AuthenticationCallback callback) {
            ParseUser.registerAuthenticationCallback(authType, callback);
        }

        public Task<ParseUser> logInWithInBackground(String authType, Map<String, String> authData) {
            return ParseUser.logInWithInBackground(authType, authData);
        }
    }

    interface ParseUserDelegate {
        void registerAuthenticationCallback(String var1, AuthenticationCallback var2);

        Task<ParseUser> logInWithInBackground(String var1, Map<String, String> var2);
    }
}

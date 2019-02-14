/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import android.os.Bundle;
import android.os.Parcel;
import androidx.annotation.NonNull;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import bolts.Continuation;
import bolts.Task;

/**
 * The {@code ParseUser} is a local representation of user data that can be saved and retrieved from
 * the Parse cloud.
 */
@SuppressWarnings("unused")
@ParseClassName("_User")
public class ParseUser extends ParseObject {

    private static final String KEY_SESSION_TOKEN = "sessionToken";
    private static final String KEY_AUTH_DATA = "authData";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_PASSWORD = "password";
    private static final String KEY_EMAIL = "email";

    private static final List<String> READ_ONLY_KEYS = Collections.unmodifiableList(
            Arrays.asList(KEY_SESSION_TOKEN, KEY_AUTH_DATA));

    private static final String PARCEL_KEY_IS_CURRENT_USER = "_isCurrentUser";
    private static final Object isAutoUserEnabledMutex = new Object();
    private static boolean autoUserEnabled;
    // Whether the object is a currentUser. If so, it will always be persisted to disk on updates.
    private boolean isCurrentUser;

    /**
     * Constructs a new ParseUser with no data in it. A ParseUser constructed in this way will not
     * have an objectId and will not persist to the database until {@link #signUp} is called.
     */
    public ParseUser() {
        isCurrentUser = false;
    }

    /**
     * Constructs a query for {@code ParseUser}.
     *
     * @see com.parse.ParseQuery#getQuery(Class)
     */
    public static ParseQuery<ParseUser> getQuery() {
        return ParseQuery.getQuery(ParseUser.class);
    }

    /* package for tests */
    static ParseUserController getUserController() {
        return ParseCorePlugins.getInstance().getUserController();
    }

    /* package for tests */
    static ParseCurrentUserController getCurrentUserController() {
        return ParseCorePlugins.getInstance().getCurrentUserController();
    }

    /* package for tests */
    static ParseAuthenticationManager getAuthenticationManager() {
        return ParseCorePlugins.getInstance().getAuthenticationManager();
    }

    /**
     * Logs in a user with a username and password. On success, this saves the session to disk, so you
     * can retrieve the currently logged in user using {@link #getCurrentUser}.
     * <p/>
     * This is preferable to using {@link #logIn}, unless your code is already running from a
     * background thread.
     *
     * @param username The username to log in with.
     * @param password The password to log in with.
     * @return A Task that is resolved when logging in completes.
     */
    public static Task<ParseUser> logInInBackground(String username, String password) {
        if (username == null) {
            throw new IllegalArgumentException("Must specify a username for the user to log in with");
        }
        if (password == null) {
            throw new IllegalArgumentException("Must specify a password for the user to log in with");
        }

        return getUserController().logInAsync(username, password).onSuccessTask(new Continuation<State, Task<ParseUser>>() {
            @Override
            public Task<ParseUser> then(Task<State> task) {
                State result = task.getResult();
                final ParseUser newCurrent = ParseObject.from(result);
                return saveCurrentUserAsync(newCurrent).onSuccess(new Continuation<Void, ParseUser>() {
                    @Override
                    public ParseUser then(Task<Void> task) {
                        return newCurrent;
                    }
                });
            }
        });
    }

    /**
     * Logs in a user with a username and password. On success, this saves the session to disk, so you
     * can retrieve the currently logged in user using {@link #getCurrentUser}.
     * <p/>
     * Typically, you should use {@link #logInInBackground} instead of this, unless you are managing
     * your own threading.
     *
     * @param username The username to log in with.
     * @param password The password to log in with.
     * @return The user if the login was successful.
     * @throws ParseException Throws an exception if the login was unsuccessful.
     */
    public static ParseUser logIn(String username, String password) throws ParseException {
        return ParseTaskUtils.wait(logInInBackground(username, password));
    }

    /**
     * Logs in a user with a username and password. On success, this saves the session to disk, so you
     * can retrieve the currently logged in user using {@link #getCurrentUser}.
     * <p/>
     * This is preferable to using {@link #logIn}, unless your code is already running from a
     * background thread.
     *
     * @param username The username to log in with.
     * @param password The password to log in with.
     * @param callback callback.done(user, e) is called when the login completes.
     */
    public static void logInInBackground(final String username, final String password,
                                         LogInCallback callback) {
        ParseTaskUtils.callbackOnMainThreadAsync(logInInBackground(username, password), callback);
    }

    /**
     * Authorize a user with a session token. On success, this saves the session to disk, so you can
     * retrieve the currently logged in user using {@link #getCurrentUser}.
     * <p/>
     * This is preferable to using {@link #become}, unless your code is already running from a
     * background thread.
     *
     * @param sessionToken The session token to authorize with.
     * @return A Task that is resolved when authorization completes.
     */
    public static Task<ParseUser> becomeInBackground(String sessionToken) {
        if (sessionToken == null) {
            throw new IllegalArgumentException("Must specify a sessionToken for the user to log in with");
        }

        return getUserController().getUserAsync(sessionToken).onSuccessTask(new Continuation<State, Task<ParseUser>>() {
            @Override
            public Task<ParseUser> then(Task<State> task) {
                State result = task.getResult();

                final ParseUser user = ParseObject.from(result);
                return saveCurrentUserAsync(user).onSuccess(new Continuation<Void, ParseUser>() {
                    @Override
                    public ParseUser then(Task<Void> task) {
                        return user;
                    }
                });
            }
        });
    }

    /**
     * Authorize a user with a session token. On success, this saves the session to disk, so you can
     * retrieve the currently logged in user using {@link #getCurrentUser}.
     * <p/>
     * Typically, you should use {@link #becomeInBackground} instead of this, unless you are managing
     * your own threading.
     *
     * @param sessionToken The session token to authorize with.
     * @return The user if the authorization was successful.
     * @throws ParseException Throws an exception if the authorization was unsuccessful.
     */
    public static ParseUser become(String sessionToken) throws ParseException {
        return ParseTaskUtils.wait(becomeInBackground(sessionToken));
    }

    /**
     * Authorize a user with a session token. On success, this saves the session to disk, so you can
     * retrieve the currently logged in user using {@link #getCurrentUser}.
     * <p/>
     * This is preferable to using {@link #become}, unless your code is already running from a
     * background thread.
     *
     * @param sessionToken The session token to authorize with.
     * @param callback     callback.done(user, e) is called when the authorization completes.
     */
    public static void becomeInBackground(final String sessionToken, LogInCallback callback) {
        ParseTaskUtils.callbackOnMainThreadAsync(becomeInBackground(sessionToken), callback);
    }

    //TODO (grantland): Publicize
    /* package */
    static Task<ParseUser> getCurrentUserAsync() {
        return getCurrentUserController().getAsync();
    }

    /**
     * This retrieves the currently logged in ParseUser with a valid session, either from memory or
     * disk if necessary.
     *
     * @return The currently logged in ParseUser
     */
    public static ParseUser getCurrentUser() {
        return getCurrentUser(isAutomaticUserEnabled());
    }

    /**
     * This retrieves the currently logged in ParseUser with a valid session, either from memory or
     * disk if necessary.
     *
     * @param shouldAutoCreateUser {@code true} to automatically create and set an anonymous user as current.
     * @return The currently logged in ParseUser
     */
    private static ParseUser getCurrentUser(boolean shouldAutoCreateUser) {
        try {
            return ParseTaskUtils.wait(getCurrentUserController().getAsync(shouldAutoCreateUser));
        } catch (ParseException e) {
            //TODO (grantland): Publicize this exception
            return null;
        }
    }

    //TODO (grantland): Make it throw ParseException and call #getCurrenSessionTokenInBackground()
    public static String getCurrentSessionToken() {
        ParseUser current = ParseUser.getCurrentUser();
        return current != null ? current.getSessionToken() : null;
    }

    //region Getter/Setter helper methods

    public static Task<String> getCurrentSessionTokenAsync() {
        return getCurrentUserController().getCurrentSessionTokenAsync();
    }

    // Persists a user as currentUser to disk, and into the singleton
    private static Task<Void> saveCurrentUserAsync(ParseUser user) {
        return getCurrentUserController().setAsync(user);
    }

    /**
     * Used by {@link ParseObject#pin} to persist lazy users to LDS that haven't been pinned yet.
     */
    /* package */
    static Task<Void> pinCurrentUserIfNeededAsync(ParseUser user) {
        if (!Parse.isLocalDatastoreEnabled()) {
            throw new IllegalStateException("Method requires Local Datastore. " +
                    "Please refer to `Parse#enableLocalDatastore(Context)`.");
        }
        return getCurrentUserController().setIfNeededAsync(user);
    }

    /**
     * Logs out the currently logged in user session. This will remove the session from disk, log out
     * of linked services, and future calls to {@link #getCurrentUser()} will return {@code null}.
     * <p/>
     * This is preferable to using {@link #logOut}, unless your code is already running from a
     * background thread.
     *
     * @return A Task that is resolved when logging out completes.
     */
    public static Task<Void> logOutInBackground() {
        return getCurrentUserController().logOutAsync();
    }

    /**
     * Logs out the currently logged in user session. This will remove the session from disk, log out
     * of linked services, and future calls to {@link #getCurrentUser()} will return {@code null}.
     * <p/>
     * This is preferable to using {@link #logOut}, unless your code is already running from a
     * background thread.
     */
    public static void logOutInBackground(LogOutCallback callback) {
        ParseTaskUtils.callbackOnMainThreadAsync(logOutInBackground(), callback);
    }

    /**
     * Logs out the currently logged in user session. This will remove the session from disk, log out
     * of linked services, and future calls to {@link #getCurrentUser()} will return {@code null}.
     * <p/>
     * Typically, you should use {@link #logOutInBackground()} instead of this, unless you are
     * managing your own threading.
     * <p/>
     * <strong>Note:</strong>: Any errors in the log out flow will be swallowed due to
     * backward-compatibility reasons. Please use {@link #logOutInBackground()} if you'd wish to
     * handle them.
     */
    public static void logOut() {
        try {
            ParseTaskUtils.wait(logOutInBackground());
        } catch (ParseException e) {
            //TODO (grantland): We shouldn't swallow errors, but we need to for backwards compatibility.
            // Change this in v2.
        }
    }

    /**
     * Requests a password reset email to be sent in a background thread to the specified email
     * address associated with the user account. This email allows the user to securely reset their
     * password on the Parse site.
     * <p/>
     * This is preferable to using {@link #requestPasswordReset(String)}, unless your code is already
     * running from a background thread.
     *
     * @param email The email address associated with the user that forgot their password.
     * @return A Task that is resolved when the command completes.
     */
    public static Task<Void> requestPasswordResetInBackground(String email) {
        return getUserController().requestPasswordResetAsync(email);
    }

    /**
     * Requests a password reset email to be sent to the specified email address associated with the
     * user account. This email allows the user to securely reset their password on the Parse site.
     * <p/>
     * Typically, you should use {@link #requestPasswordResetInBackground} instead of this, unless you
     * are managing your own threading.
     *
     * @param email The email address associated with the user that forgot their password.
     * @throws ParseException Throws an exception if the server is inaccessible, or if an account with that email
     *                        doesn't exist.
     */
    public static void requestPasswordReset(String email) throws ParseException {
        ParseTaskUtils.wait(requestPasswordResetInBackground(email));
    }

    /**
     * Requests a password reset email to be sent in a background thread to the specified email
     * address associated with the user account. This email allows the user to securely reset their
     * password on the Parse site.
     * <p/>
     * This is preferable to using {@link #requestPasswordReset(String)}, unless your code is already
     * running from a background thread.
     *
     * @param email    The email address associated with the user that forgot their password.
     * @param callback callback.done(e) is called when the request completes.
     */
    public static void requestPasswordResetInBackground(final String email,
                                                        RequestPasswordResetCallback callback) {
        ParseTaskUtils.callbackOnMainThreadAsync(requestPasswordResetInBackground(email), callback);
    }

    /**
     * Registers a third party authentication callback.
     * <p/>
     * <strong>Note:</strong> This shouldn't be called directly unless developing a third party authentication
     * library.
     *
     * @param authType The name of the third party authentication source.
     * @param callback The third party authentication callback to be registered.
     * @see AuthenticationCallback
     */
    public static void registerAuthenticationCallback(
            String authType, AuthenticationCallback callback) {
        getAuthenticationManager().register(authType, callback);
    }

    /**
     * Logs in a user with third party authentication credentials.
     * <p/>
     * <strong>Note:</strong> This shouldn't be called directly unless developing a third party authentication
     * library.
     *
     * @param authType The name of the third party authentication source.
     * @param authData The user credentials of the third party authentication source.
     * @return A {@code Task} is resolved when logging in completes.
     * @see AuthenticationCallback
     */
    public static Task<ParseUser> logInWithInBackground(
            final String authType, final Map<String, String> authData) {
        if (authType == null) {
            throw new IllegalArgumentException("Invalid authType: " + null);
        }

        final Continuation<Void, Task<ParseUser>> logInWithTask = new Continuation<Void, Task<ParseUser>>() {
            @Override
            public Task<ParseUser> then(Task<Void> task) {
                return getUserController().logInAsync(authType, authData).onSuccessTask(new Continuation<ParseUser.State, Task<ParseUser>>() {
                    @Override
                    public Task<ParseUser> then(Task<ParseUser.State> task) {
                        ParseUser.State result = task.getResult();
                        final ParseUser user = ParseObject.from(result);
                        return saveCurrentUserAsync(user).onSuccess(new Continuation<Void, ParseUser>() {
                            @Override
                            public ParseUser then(Task<Void> task) {
                                return user;
                            }
                        });
                    }
                });
            }
        };

        // Handle claiming of user.
        return getCurrentUserController().getAsync(false).onSuccessTask(new Continuation<ParseUser, Task<ParseUser>>() {
            @Override
            public Task<ParseUser> then(Task<ParseUser> task) {
                final ParseUser user = task.getResult();
                if (user != null) {
                    synchronized (user.mutex) {
                        if (ParseAnonymousUtils.isLinked(user)) {
                            if (user.isLazy()) {
                                final Map<String, String> oldAnonymousData =
                                        user.getAuthData(ParseAnonymousUtils.AUTH_TYPE);
                                return user.taskQueue.enqueue(new Continuation<Void, Task<ParseUser>>() {
                                    @Override
                                    public Task<ParseUser> then(final Task<Void> toAwait) {
                                        return toAwait.continueWithTask(new Continuation<Void, Task<Void>>() {
                                            @Override
                                            public Task<Void> then(Task<Void> task) {
                                                synchronized (user.mutex) {
                                                    // Replace any anonymity with the new linked authData.
                                                    user.stripAnonymity();
                                                    user.putAuthData(authType, authData);

                                                    return user.resolveLazinessAsync(task);
                                                }
                                            }
                                        }).continueWithTask(new Continuation<Void, Task<ParseUser>>() {
                                            @Override
                                            public Task<ParseUser> then(Task<Void> task) {
                                                synchronized (user.mutex) {
                                                    if (task.isFaulted()) {
                                                        user.removeAuthData(authType);
                                                        user.restoreAnonymity(oldAnonymousData);
                                                        return Task.forError(task.getError());
                                                    }
                                                    if (task.isCancelled()) {
                                                        return Task.cancelled();
                                                    }
                                                    return Task.forResult(user);
                                                }
                                            }
                                        });
                                    }
                                });
                            } else {
                                // Try to link the current user with third party user, unless a user is already linked
                                // to that third party user, then we'll just create a new user and link it with the
                                // third party user. New users will not be linked to the previous user's data.
                                return user.linkWithInBackground(authType, authData)
                                        .continueWithTask(new Continuation<Void, Task<ParseUser>>() {
                                            @Override
                                            public Task<ParseUser> then(Task<Void> task) {
                                                if (task.isFaulted()) {
                                                    Exception error = task.getError();
                                                    if (error instanceof ParseException
                                                            && ((ParseException) error).getCode() == ParseException.ACCOUNT_ALREADY_LINKED) {
                                                        // An account that's linked to the given authData already exists, so log in
                                                        // instead of trying to claim.
                                                        return Task.<Void>forResult(null).continueWithTask(logInWithTask);
                                                    }
                                                }
                                                if (task.isCancelled()) {
                                                    return Task.cancelled();
                                                }
                                                return Task.forResult(user);
                                            }
                                        });
                            }
                        }
                    }
                }
                return Task.<Void>forResult(null).continueWithTask(logInWithTask);
            }
        });
    }

    /**
     * Enables automatic creation of anonymous users. After calling this method,
     * {@link #getCurrentUser()} will always have a value. The user will only be created on the server
     * once the user has been saved, or once an object with a relation to that user or an ACL that
     * refers to the user has been saved.
     * <p/>
     * <strong>Note:</strong> {@link ParseObject#saveEventually()} will not work if an item being
     * saved has a relation to an automatic user that has never been saved.
     */
    public static void enableAutomaticUser() {
        synchronized (isAutoUserEnabledMutex) {
            autoUserEnabled = true;
        }
    }

    /* package */
    static void disableAutomaticUser() {
        synchronized (isAutoUserEnabledMutex) {
            autoUserEnabled = false;
        }
    }

    /* package */
    static boolean isAutomaticUserEnabled() {
        synchronized (isAutoUserEnabledMutex) {
            return autoUserEnabled;
        }
    }

    /**
     * Enables revocable sessions. This method is only required if you wish to use
     * {@link ParseSession} APIs and do not have revocable sessions enabled in your application
     * settings on your parse server.
     * <p/>
     * Upon successful completion of this {@link Task}, {@link ParseSession} APIs will be available
     * for use.
     *
     * @return A {@link Task} that will resolve when enabling revocable session
     */
    public static Task<Void> enableRevocableSessionInBackground() {
        // TODO(mengyan): Right now there is no way for us to add interceptor for this client,
        // so maybe we should move add interceptor steps to restClient()
        ParseCorePlugins.getInstance().registerUserController(
                new NetworkUserController(ParsePlugins.get().restClient(), true));

        return getCurrentUserController().getAsync(false).onSuccessTask(new Continuation<ParseUser, Task<Void>>() {
            @Override
            public Task<Void> then(Task<ParseUser> task) {
                ParseUser user = task.getResult();
                if (user == null) {
                    return Task.forResult(null);
                }
                return user.upgradeToRevocableSessionAsync();
            }
        });
    }

    //endregion

    @Override
        /* package */ boolean needsDefaultACL() {
        return false;
    }

    @Override
    boolean isKeyMutable(String key) {
        return !READ_ONLY_KEYS.contains(key);
    }

    @Override
        /* package */ State.Builder newStateBuilder(String className) {
        return new State.Builder();
    }

    @Override
        /* package */ State getState() {
        return (State) super.getState();
    }

    @Override
        /* package */ void setState(ParseObject.State newState) {
        if (isCurrentUser()) {
            State.Builder newStateBuilder = newState.newBuilder();
            // Avoid clearing sessionToken when updating the current user's State via ParseQuery result
            if (getSessionToken() != null && newState.get(KEY_SESSION_TOKEN) == null) {
                newStateBuilder.put(KEY_SESSION_TOKEN, getSessionToken());
            }
            // Avoid clearing authData when updating the current user's State via ParseQuery result
            if (getAuthData().size() > 0 && newState.get(KEY_AUTH_DATA) == null) {
                newStateBuilder.put(KEY_AUTH_DATA, getAuthData());
            }
            newState = newStateBuilder.build();
        }
        super.setState(newState);
    }

    /**
     * @return {@code true} if this user was created with {@link #getCurrentUser()} when no current
     * user previously existed and {@link #enableAutomaticUser()} is set, false if was created by any
     * other means or if a previously "lazy" user was saved remotely.
     */
    /* package */ boolean isLazy() {
        synchronized (mutex) {
            return getObjectId() == null && ParseAnonymousUtils.isLinked(this);
        }
    }

    /**
     * Whether the ParseUser has been authenticated on this device. This will be true if the ParseUser
     * was obtained via a logIn or signUp method. Only an authenticated ParseUser can be saved (with
     * altered attributes) and deleted.
     */
    public boolean isAuthenticated() {
        synchronized (mutex) {
            ParseUser current = ParseUser.getCurrentUser();
            return isLazy() ||
                    (getState().sessionToken() != null
                            && current != null
                            && getObjectId().equals(current.getObjectId()));
        }
    }

    @Override
    public void remove(@NonNull String key) {
        if (KEY_USERNAME.equals(key)) {
            throw new IllegalArgumentException("Can't remove the username key.");
        }
        super.remove(key);
    }

    @Override
        /* package */ JSONObject toRest(
            ParseObject.State state,
            List<ParseOperationSet> operationSetQueue,
            ParseEncoder objectEncoder) {
        // Create a sanitized copy of operationSetQueue with `password` removed if necessary
        List<ParseOperationSet> cleanOperationSetQueue = operationSetQueue;
        for (int i = 0; i < operationSetQueue.size(); i++) {
            ParseOperationSet operations = operationSetQueue.get(i);
            if (operations.containsKey(KEY_PASSWORD)) {
                if (cleanOperationSetQueue == operationSetQueue) {
                    cleanOperationSetQueue = new LinkedList<>(operationSetQueue);
                }
                ParseOperationSet cleanOperations = new ParseOperationSet(operations);
                cleanOperations.remove(KEY_PASSWORD);
                cleanOperationSetQueue.set(i, cleanOperations);
            }
        }
        return super.toRest(state, cleanOperationSetQueue, objectEncoder);
    }

    /* package for tests */ Task<Void> cleanUpAuthDataAsync() {
        ParseAuthenticationManager controller = getAuthenticationManager();
        Map<String, Map<String, String>> authData;
        synchronized (mutex) {
            authData = getState().authData();
            if (authData.size() == 0) {
                return Task.forResult(null); // Nothing to see or do here...
            }
        }

        List<Task<Void>> tasks = new ArrayList<>();
        Iterator<Map.Entry<String, Map<String, String>>> i = authData.entrySet().iterator();
        while (i.hasNext()) {
            Map.Entry<String, Map<String, String>> entry = i.next();
            if (entry.getValue() == null) {
                i.remove();
                tasks.add(controller.restoreAuthenticationAsync(entry.getKey(), null).makeVoid());
            }
        }

        State newState = getState().newBuilder()
                .authData(authData)
                .build();
        setState(newState);

        return Task.whenAll(tasks);
    }

    @Override
        /* package */ Task<Void> handleSaveResultAsync(
            ParseObject.State result, ParseOperationSet operationsBeforeSave) {
        boolean success = result != null;
        if (success) {
            operationsBeforeSave.remove(KEY_PASSWORD);
        }

        return super.handleSaveResultAsync(result, operationsBeforeSave);
    }

    @Override
        /* package */ void validateSaveEventually() throws ParseException {
        if (isDirty(KEY_PASSWORD)) {
            // TODO(mengyan): Unify the exception we throw when validate fails
            throw new ParseException(
                    ParseException.OTHER_CAUSE,
                    "Unable to saveEventually on a ParseUser with dirty password");
        }
    }

    /* package */ boolean isCurrentUser() {
        synchronized (mutex) {
            return isCurrentUser;
        }
    }

    /* package */ void setIsCurrentUser(boolean isCurrentUser) {
        synchronized (mutex) {
            this.isCurrentUser = isCurrentUser;
        }
    }

    /**
     * @return the session token for a user, if they are logged in.
     */
    public String getSessionToken() {
        return getState().sessionToken();
    }

    // This is only used when upgrading to revocable session
    private Task<Void> setSessionTokenInBackground(String newSessionToken) {
        synchronized (mutex) {
            State state = getState();
            if (newSessionToken.equals(state.sessionToken())) {
                return Task.forResult(null);
            }

            State.Builder builder = state.newBuilder()
                    .sessionToken(newSessionToken);
            setState(builder.build());
            return saveCurrentUserAsync(this);
        }
    }

    /* package for testes */ Map<String, Map<String, String>> getAuthData() {
        synchronized (mutex) {
            Map<String, Map<String, String>> authData = getMap(KEY_AUTH_DATA);
            if (authData == null) {
                // We'll always return non-null for now since we don't have any null checking in place.
                // Be aware not to get and set without checking size or else we'll be adding a value that
                // wasn't there in the first place.
                authData = new HashMap<>();
            }
            return authData;
        }
    }

    private Map<String, String> getAuthData(String authType) {
        return getAuthData().get(authType);
    }

    /* package */ void putAuthData(String authType, Map<String, String> authData) {
        synchronized (mutex) {
            Map<String, Map<String, String>> newAuthData = getAuthData();
            newAuthData.put(authType, authData);
            performPut(KEY_AUTH_DATA, newAuthData);
        }
    }

    private void removeAuthData(String authType) {
        synchronized (mutex) {
            Map<String, Map<String, String>> newAuthData = getAuthData();
            newAuthData.remove(authType);
            performPut(KEY_AUTH_DATA, newAuthData);
        }
    }

    /**
     * Retrieves the username.
     */
    public String getUsername() {
        return getString(KEY_USERNAME);
    }

    /**
     * Sets the username. Usernames cannot be null or blank.
     *
     * @param username The username to set.
     */
    public void setUsername(String username) {
        put(KEY_USERNAME, username);
    }

    /* package for tests */ String getPassword() {
        return getString(KEY_PASSWORD);
    }

    /**
     * Sets the password.
     *
     * @param password The password to set.
     */
    public void setPassword(String password) {
        put(KEY_PASSWORD, password);
    }

    /**
     * Retrieves the email address.
     */
    public String getEmail() {
        return getString(KEY_EMAIL);
    }

    /**
     * Sets the email address.
     *
     * @param email The email address to set.
     */
    public void setEmail(String email) {
        put(KEY_EMAIL, email);
    }

    /**
     * Indicates whether this {@code ParseUser} was created during this session through a call to
     * {@link #signUp()} or by logging in with a linked service such as Facebook.
     */
    public boolean isNew() {
        return getState().isNew();
    }

    @Override
    public void put(@NonNull String key, @NonNull Object value) {
        synchronized (mutex) {
            if (KEY_USERNAME.equals(key)) {
                // When the username is set, remove any semblance of anonymity.
                stripAnonymity();
            }
            super.put(key, value);
        }
    }

    private void stripAnonymity() {
        synchronized (mutex) {
            if (ParseAnonymousUtils.isLinked(this)) {
                if (getObjectId() != null) {
                    putAuthData(ParseAnonymousUtils.AUTH_TYPE, null);
                } else {
                    removeAuthData(ParseAnonymousUtils.AUTH_TYPE);
                }
            }
        }
    }

    // TODO(grantland): Can we replace this with #revert(String)?
    private void restoreAnonymity(Map<String, String> anonymousData) {
        synchronized (mutex) {
            if (anonymousData != null) {
                putAuthData(ParseAnonymousUtils.AUTH_TYPE, anonymousData);
            }
        }
    }

    @Override
        /* package */ void validateSave() {
        synchronized (mutex) {
            if (getObjectId() == null) {
                throw new IllegalArgumentException(
                        "Cannot save a ParseUser until it has been signed up. Call signUp first.");
            }

            if (isAuthenticated() || !isDirty() || isCurrentUser()) {
                return;
            }
        }

        if (!Parse.isLocalDatastoreEnabled()) {
            // This might be a different of instance of the currentUser, so we need to check objectIds
            ParseUser current = ParseUser.getCurrentUser(); //TODO (grantland): possible blocking disk i/o
            if (current != null && getObjectId().equals(current.getObjectId())) {
                return;
            }
        }

        throw new IllegalArgumentException("Cannot save a ParseUser that is not authenticated.");
    }

    @Override
        /* package */ Task<Void> saveAsync(String sessionToken, Task<Void> toAwait) {
        return saveAsync(sessionToken, isLazy(), toAwait);
    }

    /* package for tests */ Task<Void> saveAsync(String sessionToken, boolean isLazy, Task<Void> toAwait) {
        Task<Void> task;
        if (isLazy) {
            task = resolveLazinessAsync(toAwait);
        } else {
            task = super.saveAsync(sessionToken, toAwait);
        }

        if (isCurrentUser()) {
            // If the user is the currently logged in user, we persist all data to disk
            return task.onSuccessTask(new Continuation<Void, Task<Void>>() {
                @Override
                public Task<Void> then(Task<Void> task) {
                    return cleanUpAuthDataAsync();
                }
            }).onSuccessTask(new Continuation<Void, Task<Void>>() {
                @Override
                public Task<Void> then(Task<Void> task) {
                    return saveCurrentUserAsync(ParseUser.this);
                }
            });
        }

        return task;
    }

    @Override
        /* package */ void validateDelete() {
        synchronized (mutex) {
            super.validateDelete();
            if (!isAuthenticated() && isDirty()) {
                throw new IllegalArgumentException("Cannot delete a ParseUser that is not authenticated.");
            }
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public ParseUser fetch() throws ParseException {
        return (ParseUser) super.fetch();
    }

    @SuppressWarnings("unchecked")
    @Override
        /* package */ <T extends ParseObject> Task<T> fetchAsync(
            String sessionToken, Task<Void> toAwait) {
        //TODO (grantland): It doesn't seem like we should do this.. Why don't we error like we do
        // when fetching an unsaved ParseObject?
        if (isLazy()) {
            return Task.forResult((T) this);
        }

        Task<T> task = super.fetchAsync(sessionToken, toAwait);

        if (isCurrentUser()) {
            return task.onSuccessTask(new Continuation<T, Task<Void>>() {
                @Override
                public Task<Void> then(final Task<T> fetchAsyncTask) {
                    return cleanUpAuthDataAsync();
                }
            }).onSuccessTask(new Continuation<Void, Task<Void>>() {
                @Override
                public Task<Void> then(Task<Void> task) {
                    return saveCurrentUserAsync(ParseUser.this);
                }
            }).onSuccess(new Continuation<Void, T>() {
                @Override
                public T then(Task<Void> task) {
                    return (T) ParseUser.this;
                }
            });
        }

        return task;
    }

    //region Third party authentication

    /**
     * Signs up a new user. You should call this instead of {@link #save} for new ParseUsers. This
     * will create a new ParseUser on the server, and also persist the session on disk so that you can
     * access the user using {@link #getCurrentUser}.
     * <p/>
     * A username and password must be set before calling signUp.
     * <p/>
     * This is preferable to using {@link #signUp}, unless your code is already running from a
     * background thread.
     *
     * @return A Task that is resolved when sign up completes.
     */
    public Task<Void> signUpInBackground() {
        return taskQueue.enqueue(new Continuation<Void, Task<Void>>() {
            @Override
            public Task<Void> then(Task<Void> task) {
                return signUpAsync(task);
            }
        });
    }

    /* package for tests */ Task<Void> signUpAsync(Task<Void> toAwait) {
        final ParseUser user = getCurrentUser(); //TODO (grantland): convert to async
        synchronized (mutex) {
            final String sessionToken = user != null ? user.getSessionToken() : null;
            if (ParseTextUtils.isEmpty(getUsername())) {
                return Task.forError(new IllegalArgumentException("Username cannot be missing or blank"));
            }

            if (ParseTextUtils.isEmpty(getPassword())) {
                return Task.forError(new IllegalArgumentException("Password cannot be missing or blank"));
            }

            if (getObjectId() != null) {
                // For anonymous users, there may be an objectId. Setting the
                // userName will have removed the anonymous link and set the
                // value in the authData object to JSONObject.NULL, so we can
                // just treat it like a save operation.
                Map<String, Map<String, String>> authData = getAuthData();
                if (authData.containsKey(ParseAnonymousUtils.AUTH_TYPE)
                        && authData.get(ParseAnonymousUtils.AUTH_TYPE) == null) {
                    return saveAsync(sessionToken, toAwait);
                }

                // Otherwise, throw.
                return Task.forError(
                        new IllegalArgumentException("Cannot sign up a user that has already signed up."));
            }

            // If the operationSetQueue is has operation sets in it, then a save or signUp is in progress.
            // If there is a signUp or save already in progress, don't allow another one to start.
            if (operationSetQueue.size() > 1) {
                return Task.forError(
                        new IllegalArgumentException("Cannot sign up a user that is already signing up."));
            }

            // If the current user is an anonymous user, merge this object's data into the anonymous user
            // and save.
            if (user != null && ParseAnonymousUtils.isLinked(user)) {
                // this doesn't have any outstanding saves, so we can safely merge its operations into the
                // current user.

                if (this == user) {
                    return Task.forError(
                            new IllegalArgumentException("Attempt to merge currentUser with itself."));
                }

                boolean isLazy = user.isLazy();
                final String oldUsername = user.getUsername();
                final String oldPassword = user.getPassword();
                final Map<String, String> anonymousData = user.getAuthData(ParseAnonymousUtils.AUTH_TYPE);

                user.copyChangesFrom(this);
                user.setUsername(getUsername());
                user.setPassword(getPassword());
                revert();

                return user.saveAsync(sessionToken, isLazy, toAwait).continueWithTask(new Continuation<Void, Task<Void>>() {
                    @Override
                    public Task<Void> then(Task<Void> task) {
                        if (task.isCancelled() || task.isFaulted()) { // Error
                            synchronized (user.mutex) {
                                if (oldUsername != null) {
                                    user.setUsername(oldUsername);
                                } else {
                                    user.revert(KEY_USERNAME);
                                }
                                if (oldPassword != null) {
                                    user.setPassword(oldPassword);
                                } else {
                                    user.revert(KEY_PASSWORD);
                                }
                                user.restoreAnonymity(anonymousData);
                            }
                            return task;
                        } else { // Success
                            user.revert(KEY_PASSWORD);
                            revert(KEY_PASSWORD);
                        }

                        mergeFromObject(user);
                        return saveCurrentUserAsync(ParseUser.this);
                    }
                });
            }

            final ParseOperationSet operations = startSave();

            return toAwait.onSuccessTask(new Continuation<Void, Task<Void>>() {
                @Override
                public Task<Void> then(Task<Void> task) {
                    return getUserController().signUpAsync(
                            getState(), operations, sessionToken
                    ).continueWithTask(new Continuation<ParseUser.State, Task<Void>>() {
                        @Override
                        public Task<Void> then(final Task<ParseUser.State> signUpTask) {
                            ParseUser.State result = signUpTask.getResult();
                            return handleSaveResultAsync(result,
                                    operations).continueWithTask(new Continuation<Void, Task<Void>>() {
                                @Override
                                public Task<Void> then(Task<Void> task) {
                                    if (!signUpTask.isCancelled() && !signUpTask.isFaulted()) {
                                        return saveCurrentUserAsync(ParseUser.this);
                                    }
                                    return signUpTask.makeVoid();
                                }
                            });
                        }
                    });
                }
            });
        }
    }

    /**
     * Signs up a new user. You should call this instead of {@link #save} for new ParseUsers. This
     * will create a new ParseUser on the server, and also persist the session on disk so that you can
     * access the user using {@link #getCurrentUser}.
     * <p/>
     * A username and password must be set before calling signUp.
     * <p/>
     * Typically, you should use {@link #signUpInBackground} instead of this, unless you are managing
     * your own threading.
     *
     * @throws ParseException Throws an exception if the server is inaccessible, or if the username has already
     *                        been taken.
     */
    public void signUp() throws ParseException {
        ParseTaskUtils.wait(signUpInBackground());
    }

    /**
     * Signs up a new user. You should call this instead of {@link #save} for new ParseUsers. This
     * will create a new ParseUser on the server, and also persist the session on disk so that you can
     * access the user using {@link #getCurrentUser}.
     * <p/>
     * A username and password must be set before calling signUp.
     * <p/>
     * This is preferable to using {@link #signUp}, unless your code is already running from a
     * background thread.
     *
     * @param callback callback.done(user, e) is called when the signUp completes.
     */
    public void signUpInBackground(SignUpCallback callback) {
        ParseTaskUtils.callbackOnMainThreadAsync(signUpInBackground(), callback);
    }

    //TODO (grantland): Add to taskQueue
    /* package */ Task<Void> logOutAsync() {
        return logOutAsync(true);
    }

    /* package */ Task<Void> logOutAsync(boolean revoke) {
        ParseAuthenticationManager controller = getAuthenticationManager();
        List<Task<Void>> tasks = new ArrayList<>();
        final String oldSessionToken;

        synchronized (mutex) {
            oldSessionToken = getState().sessionToken();

            for (Map.Entry<String, Map<String, String>> entry : getAuthData().entrySet()) {
                tasks.add(controller.deauthenticateAsync(entry.getKey()));
            }

            State newState = getState().newBuilder()
                    .sessionToken(null)
                    .isNew(false)
                    .build();
            isCurrentUser = false;
            setState(newState);
        }

        if (revoke) {
            tasks.add(ParseSession.revokeAsync(oldSessionToken));
        }

        return Task.whenAll(tasks);
    }

    @SuppressWarnings("unchecked")
    @Override
    public ParseUser fetchIfNeeded() throws ParseException {
        return super.fetchIfNeeded();
    }

    /**
     * Indicates whether this user is linked with a third party authentication source.
     * <p/>
     * <strong>Note:</strong> This shouldn't be called directly unless developing a third party authentication
     * library.
     *
     * @param authType The name of the third party authentication source.
     * @return {@code true} if linked, otherwise {@code false}.
     * @see AuthenticationCallback
     */
    public boolean isLinked(String authType) {
        Map<String, Map<String, String>> authData = getAuthData();
        return authData.containsKey(authType) && authData.get(authType) != null;
    }

    /**
     * Ensures that all auth sources have auth data (e.g. access tokens, etc.) that matches this
     * user.
     */
    /* package */ Task<Void> synchronizeAllAuthDataAsync() {
        Map<String, Map<String, String>> authData;
        synchronized (mutex) {
            if (!isCurrentUser()) {
                return Task.forResult(null);
            }
            authData = getAuthData();
        }
        List<Task<Void>> tasks = new ArrayList<>(authData.size());
        for (String authType : authData.keySet()) {
            tasks.add(synchronizeAuthDataAsync(authType));
        }
        return Task.whenAll(tasks);
    }

    /* package */ Task<Void> synchronizeAuthDataAsync(String authType) {
        Map<String, String> authData;
        synchronized (mutex) {
            if (!isCurrentUser()) {
                return Task.forResult(null);
            }
            authData = getAuthData(authType);
        }
        return synchronizeAuthDataAsync(getAuthenticationManager(), authType, authData);
    }

    //endregion

    private Task<Void> synchronizeAuthDataAsync(
            ParseAuthenticationManager manager, final String authType, Map<String, String> authData) {
        return manager.restoreAuthenticationAsync(authType, authData).continueWithTask(new Continuation<Boolean, Task<Void>>() {
            @Override
            public Task<Void> then(Task<Boolean> task) {
                boolean success = !task.isFaulted() && task.getResult();
                if (!success) {
                    return unlinkFromInBackground(authType);
                }
                return task.makeVoid();
            }
        });
    }

    private Task<Void> linkWithAsync(
            final String authType,
            final Map<String, String> authData,
            final Task<Void> toAwait,
            final String sessionToken) {
        synchronized (mutex) {
            final boolean isLazy = isLazy();
            final Map<String, String> oldAnonymousData = getAuthData(ParseAnonymousUtils.AUTH_TYPE);

            stripAnonymity();
            putAuthData(authType, authData);

            return saveAsync(sessionToken, isLazy, toAwait).continueWithTask(new Continuation<Void, Task<Void>>() {
                @Override
                public Task<Void> then(Task<Void> task) {
                    synchronized (mutex) {
                        if (task.isFaulted() || task.isCancelled()) {
                            removeAuthData(authType);
                            restoreAnonymity(oldAnonymousData);
                            return task;
                        }
                        return synchronizeAuthDataAsync(authType);
                    }
                }
            });
        }
    }

    //region Automatic User

    private Task<Void> linkWithAsync(
            final String authType,
            final Map<String, String> authData,
            final String sessionToken) {
        return taskQueue.enqueue(new Continuation<Void, Task<Void>>() {
            @Override
            public Task<Void> then(Task<Void> task) {
                return linkWithAsync(authType, authData, task, sessionToken);
            }
        });
    }

    /**
     * Links this user to a third party authentication source.
     * <p/>
     * <strong>Note:</strong> This shouldn't be called directly unless developing a third party authentication
     * library.
     *
     * @param authType The name of the third party authentication source.
     * @param authData The user credentials of the third party authentication source.
     * @return A {@code Task} is resolved when linking completes.
     * @see AuthenticationCallback
     */
    public Task<Void> linkWithInBackground(
            String authType, Map<String, String> authData) {
        if (authType == null) {
            throw new IllegalArgumentException("Invalid authType: " + null);
        }
        return linkWithAsync(authType, authData, getSessionToken());
    }

    /**
     * Unlinks this user from a third party authentication source.
     * <p/>
     * <strong>Note:</strong> This shouldn't be called directly unless developing a third party authentication
     * library.
     *
     * @param authType The name of the third party authentication source.
     * @return A {@code Task} is resolved when unlinking completes.
     * @see AuthenticationCallback
     */
    public Task<Void> unlinkFromInBackground(final String authType) {
        if (authType == null) {
            return Task.forResult(null);
        }

        synchronized (mutex) {
            if (!getAuthData().containsKey(authType)) {
                return Task.forResult(null);
            }
            putAuthData(authType, null);
        }

        return saveInBackground();
    }

    /**
     * Try to resolve a lazy user.
     * <p>
     * If {@code authData} is empty, we'll treat this just as a SignUp. Otherwise, we'll
     * treat this as a SignUpOrLogIn. We'll merge the server result with this user, only if LDS is not
     * enabled.
     *
     * @param toAwait {@code Task} to wait for completion before running.
     * @return A {@code Task} that will resolve to the current user. If this is a SignUp it'll be this
     * {@code ParseUser} instance, otherwise it'll be a new {@code ParseUser} instance.
     */
    /* package for tests */ Task<Void> resolveLazinessAsync(Task<Void> toAwait) {
        synchronized (mutex) {
            if (getAuthData().size() == 0) { // TODO(grantland): Could we just check isDirty(KEY_AUTH_DATA)?
                // If there are no linked services, treat this as a SignUp.
                return signUpAsync(toAwait);
            }

            final ParseOperationSet operations = startSave();

            // Otherwise, treat this as a SignUpOrLogIn
            return toAwait.onSuccessTask(new Continuation<Void, Task<Void>>() {
                @Override
                public Task<Void> then(Task<Void> task) {
                    return getUserController().logInAsync(getState(), operations).onSuccessTask(new Continuation<ParseUser.State, Task<Void>>() {
                        @Override
                        public Task<Void> then(Task<ParseUser.State> task) {
                            final ParseUser.State result = task.getResult();

                            Task<ParseUser.State> resultTask;
                            // We can't merge this user with the server if this is a LogIn because LDS might
                            // already be keeping track of the servers objectId.
                            if (Parse.isLocalDatastoreEnabled() && !result.isNew()) {
                                resultTask = Task.forResult(result);
                            } else {
                                resultTask = handleSaveResultAsync(result,
                                        operations).onSuccess(new Continuation<Void, ParseUser.State>() {
                                    @Override
                                    public ParseUser.State then(Task<Void> task) {
                                        return result;
                                    }
                                });
                            }
                            return resultTask.onSuccessTask(new Continuation<ParseUser.State, Task<Void>>() {
                                @Override
                                public Task<Void> then(Task<ParseUser.State> task) {
                                    ParseUser.State result = task.getResult();
                                    if (!result.isNew()) {
                                        // If the result is not a new user, treat this as a fresh logIn with complete
                                        // serverData, and switch the current user to the new user.
                                        final ParseUser newUser = ParseObject.from(result);
                                        return saveCurrentUserAsync(newUser);
                                    }
                                    return task.makeVoid();
                                }
                            });
                        }
                    });
                }
            });
        }
    }

    @SuppressWarnings("unchecked")
    @Override
        /* package */ <T extends ParseObject> Task<T> fetchFromLocalDatastoreAsync() {
        // Same as #fetch()
        if (isLazy()) {
            return Task.forResult((T) this);
        }
        return super.fetchFromLocalDatastoreAsync();
    }

    //endregion

    //region Parcelable

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        synchronized (mutex) {
            outState.putBoolean(PARCEL_KEY_IS_CURRENT_USER, isCurrentUser);
        }
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedState) {
        super.onRestoreInstanceState(savedState);
        setIsCurrentUser(savedState.getBoolean(PARCEL_KEY_IS_CURRENT_USER, false));
    }

    //endregion

    //region Legacy/Revocable Session Tokens

    /* package */ Task<Void> upgradeToRevocableSessionAsync() {
        return taskQueue.enqueue(new Continuation<Void, Task<Void>>() {
            @Override
            public Task<Void> then(Task<Void> toAwait) {
                return upgradeToRevocableSessionAsync(toAwait);
            }
        });
    }

    private Task<Void> upgradeToRevocableSessionAsync(Task<Void> toAwait) {
        final String sessionToken = getSessionToken();
        return toAwait.continueWithTask(new Continuation<Void, Task<String>>() {
            @Override
            public Task<String> then(Task<Void> task) {
                return ParseSession.upgradeToRevocableSessionAsync(sessionToken);
            }
        }).onSuccessTask(new Continuation<String, Task<Void>>() {
            @Override
            public Task<Void> then(Task<String> task) {
                String result = task.getResult();
                return setSessionTokenInBackground(result);
            }
        });
    }

    static class State extends ParseObject.State {

        private final boolean isNew;

        private State(Builder builder) {
            super(builder);
            isNew = builder.isNew;
        }

        /* package */ State(Parcel source, String className, ParseParcelDecoder decoder) {
            super(source, className, decoder);
            isNew = source.readByte() == 1;
        }

        @SuppressWarnings("unchecked")
        @Override
        public Builder newBuilder() {
            return new Builder(this);
        }

        public String sessionToken() {
            return (String) get(KEY_SESSION_TOKEN);
        }

        @SuppressWarnings("unchecked")
        public Map<String, Map<String, String>> authData() {
            Map<String, Map<String, String>> authData =
                    (Map<String, Map<String, String>>) get(KEY_AUTH_DATA);
            if (authData == null) {
                // We'll always return non-null for now since we don't have any null checking in place.
                // Be aware not to get and set without checking size or else we'll be adding a value that
                // wasn't there in the first place.
                authData = new HashMap<>();
            }
            return authData;
        }

        public boolean isNew() {
            return isNew;
        }

        @Override
        protected void writeToParcel(Parcel dest, ParseParcelEncoder encoder) {
            super.writeToParcel(dest, encoder);
            dest.writeByte(isNew ? (byte) 1 : 0);
        }

        static class Builder extends Init<Builder> {

            private boolean isNew;

            public Builder() {
                super("_User");
            }

            /* package */ Builder(State state) {
                super(state);
                isNew = state.isNew();
            }

            @Override
                /* package */ Builder self() {
                return this;
            }

            @SuppressWarnings("unchecked")
            @Override
            public State build() {
                return new State(this);
            }

            @Override
            public Builder apply(ParseObject.State other) {
                isNew(((State) other).isNew());
                return super.apply(other);
            }

            public Builder sessionToken(String sessionToken) {
                return put(KEY_SESSION_TOKEN, sessionToken);
            }

            public Builder authData(Map<String, Map<String, String>> authData) {
                return put(KEY_AUTH_DATA, authData);
            }

            @SuppressWarnings("unchecked")
            public Builder putAuthData(String authType, Map<String, String> authData) {
                Map<String, Map<String, String>> newAuthData =
                        (Map<String, Map<String, String>>) serverData.get(KEY_AUTH_DATA);
                if (newAuthData == null) {
                    newAuthData = new HashMap<>();
                }
                newAuthData.put(authType, authData);
                serverData.put(KEY_AUTH_DATA, newAuthData);
                return this;
            }

            public Builder isNew(boolean isNew) {
                this.isNew = isNew;
                return this;
            }
        }
    }

    //endregion
}

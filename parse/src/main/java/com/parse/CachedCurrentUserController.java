/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import com.parse.boltsinternal.Task;
import java.util.Arrays;
import java.util.Map;

class CachedCurrentUserController implements ParseCurrentUserController {

    /**
     * Lock used to synchronize current user modifications and access.
     *
     * <p>Note about lock ordering:
     *
     * <p>You must NOT acquire the ParseUser instance mutex (the "mutex" field in ParseObject) while
     * holding this static initialization lock. Doing so will cause a deadlock.
     */
    private final Object mutex = new Object();

    private final TaskQueue taskQueue = new TaskQueue();

    private final ParseObjectStore<ParseUser> store;

    /* package */ ParseUser currentUser;
    // Whether currentUser is known to match the serialized version on disk. This is useful for
    // saving
    // a filesystem check if you try to load currentUser frequently while there is none on disk.
    /* package */ boolean currentUserMatchesDisk = false;

    public CachedCurrentUserController(ParseObjectStore<ParseUser> store) {
        this.store = store;
    }

    @Override
    public Task<Void> setAsync(final ParseUser user) {
        return taskQueue.enqueue(
                toAwait ->
                        toAwait.continueWithTask(
                                        task -> {
                                            ParseUser oldCurrentUser;
                                            synchronized (mutex) {
                                                oldCurrentUser = currentUser;
                                            }

                                            if (oldCurrentUser != null && oldCurrentUser != user) {
                                                // We don't need to revoke the token since we're not
                                                // explicitly calling logOut
                                                // We don't need to remove persisted files since
                                                // we're overwriting them
                                                return oldCurrentUser
                                                        .logOutAsync(false)
                                                        .continueWith(
                                                                task1 -> {
                                                                    return null; // ignore errors
                                                                });
                                            }
                                            return task;
                                        })
                                .onSuccessTask(
                                        task -> {
                                            user.setIsCurrentUser(true);
                                            return user.synchronizeAllAuthDataAsync();
                                        })
                                .onSuccessTask(
                                        task ->
                                                store.setAsync(user)
                                                        .continueWith(
                                                                task12 -> {
                                                                    synchronized (mutex) {
                                                                        currentUserMatchesDisk =
                                                                                !task12.isFaulted();
                                                                        currentUser = user;
                                                                    }
                                                                    return null;
                                                                })));
    }

    @Override
    public Task<Void> setIfNeededAsync(ParseUser user) {
        synchronized (mutex) {
            if (!user.isCurrentUser() || currentUserMatchesDisk) {
                return Task.forResult(null);
            }
        }

        return setAsync(user);
    }

    @Override
    public Task<ParseUser> getAsync() {
        return getAsync(ParseUser.isAutomaticUserEnabled());
    }

    @Override
    public Task<Boolean> existsAsync() {
        synchronized (mutex) {
            if (currentUser != null) {
                return Task.forResult(true);
            }
        }

        return taskQueue.enqueue(toAwait -> toAwait.continueWithTask(task -> store.existsAsync()));
    }

    @Override
    public boolean isCurrent(ParseUser user) {
        synchronized (mutex) {
            return currentUser == user;
        }
    }

    @Override
    public void clearFromMemory() {
        synchronized (mutex) {
            currentUser = null;
            currentUserMatchesDisk = false;
        }
    }

    @Override
    public void clearFromDisk() {
        synchronized (mutex) {
            currentUser = null;
            currentUserMatchesDisk = false;
        }
        try {
            ParseTaskUtils.wait(store.deleteAsync());
        } catch (ParseException e) {
            // ignored
        }
    }

    @Override
    public Task<String> getCurrentSessionTokenAsync() {
        return getAsync(false)
                .onSuccess(
                        task -> {
                            ParseUser user = task.getResult();
                            return user != null ? user.getSessionToken() : null;
                        });
    }

    @Override
    public Task<Void> logOutAsync() {
        return taskQueue.enqueue(
                toAwait -> {
                    // We can parallelize disk and network work, but only after we restore the
                    // current user from
                    // disk.
                    final Task<ParseUser> userTask = getAsync(false);
                    return Task.whenAll(Arrays.asList(userTask, toAwait))
                            .continueWithTask(
                                    task -> {
                                        Task<Void> logOutTask =
                                                userTask.onSuccessTask(
                                                        task1 -> {
                                                            ParseUser user = task1.getResult();
                                                            if (user == null) {
                                                                return task1.cast();
                                                            }
                                                            return user.logOutAsync();
                                                        });

                                        Task<Void> diskTask =
                                                store.deleteAsync()
                                                        .continueWith(
                                                                task12 -> {
                                                                    boolean deleted =
                                                                            !task12.isFaulted();
                                                                    synchronized (mutex) {
                                                                        currentUserMatchesDisk =
                                                                                deleted;
                                                                        currentUser = null;
                                                                    }
                                                                    return null;
                                                                });
                                        return Task.whenAll(Arrays.asList(logOutTask, diskTask));
                                    });
                });
    }

    @Override
    public Task<ParseUser> getAsync(final boolean shouldAutoCreateUser) {
        synchronized (mutex) {
            if (currentUser != null) {
                return Task.forResult(currentUser);
            }
        }

        return taskQueue.enqueue(
                toAwait ->
                        toAwait.continueWithTask(
                                ignored -> {
                                    ParseUser current;
                                    boolean matchesDisk;
                                    synchronized (mutex) {
                                        current = currentUser;
                                        matchesDisk = currentUserMatchesDisk;
                                    }

                                    if (current != null) {
                                        return Task.forResult(current);
                                    }

                                    if (matchesDisk) {
                                        if (shouldAutoCreateUser) {
                                            return Task.forResult(lazyLogIn());
                                        }
                                        return null;
                                    }

                                    return store.getAsync()
                                            .continueWith(
                                                    task -> {
                                                        ParseUser current1 = task.getResult();
                                                        boolean matchesDisk1 = !task.isFaulted();

                                                        synchronized (mutex) {
                                                            currentUser = current1;
                                                            currentUserMatchesDisk = matchesDisk1;
                                                        }

                                                        if (current1 != null) {
                                                            synchronized (current1.mutex) {
                                                                current1.setIsCurrentUser(true);
                                                            }
                                                            return current1;
                                                        }

                                                        if (shouldAutoCreateUser) {
                                                            return lazyLogIn();
                                                        }
                                                        return null;
                                                    });
                                }));
    }

    private ParseUser lazyLogIn() {
        Map<String, String> authData = ParseAnonymousUtils.getAuthData();
        return lazyLogIn(ParseAnonymousUtils.AUTH_TYPE, authData);
    }

    /* package for tests */ ParseUser lazyLogIn(String authType, Map<String, String> authData) {
        // Note: if authType != ParseAnonymousUtils.AUTH_TYPE the user is not "lazy".
        ParseUser user = ParseObject.create(ParseUser.class);
        synchronized (user.mutex) {
            user.setIsCurrentUser(true);
            user.putAuthData(authType, authData);
        }

        synchronized (mutex) {
            currentUserMatchesDisk = false;
            currentUser = user;
        }

        return user;
    }
}

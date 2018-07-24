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
import java.util.Map;

import bolts.Continuation;
import bolts.Task;

class CachedCurrentUserController implements ParseCurrentUserController {

    /**
     * Lock used to synchronize current user modifications and access.
     * <p>
     * Note about lock ordering:
     * <p>
     * You must NOT acquire the ParseUser instance mutex (the "mutex" field in ParseObject) while
     * holding this static initialization lock. Doing so will cause a deadlock.
     */
    private final Object mutex = new Object();
    private final TaskQueue taskQueue = new TaskQueue();

    private final ParseObjectStore<ParseUser> store;

    /* package */ ParseUser currentUser;
    // Whether currentUser is known to match the serialized version on disk. This is useful for saving
    // a filesystem check if you try to load currentUser frequently while there is none on disk.
    /* package */ boolean currentUserMatchesDisk = false;

    public CachedCurrentUserController(ParseObjectStore<ParseUser> store) {
        this.store = store;
    }

    @Override
    public Task<Void> setAsync(final ParseUser user) {
        return taskQueue.enqueue(new Continuation<Void, Task<Void>>() {
            @Override
            public Task<Void> then(Task<Void> toAwait) {
                return toAwait.continueWithTask(new Continuation<Void, Task<Void>>() {
                    @Override
                    public Task<Void> then(Task<Void> task) {
                        ParseUser oldCurrentUser;
                        synchronized (mutex) {
                            oldCurrentUser = currentUser;
                        }

                        if (oldCurrentUser != null && oldCurrentUser != user) {
                            // We don't need to revoke the token since we're not explicitly calling logOut
                            // We don't need to remove persisted files since we're overwriting them
                            return oldCurrentUser.logOutAsync(false).continueWith(new Continuation<Void, Void>() {
                                @Override
                                public Void then(Task<Void> task) {
                                    return null; // ignore errors
                                }
                            });
                        }
                        return task;
                    }
                }).onSuccessTask(new Continuation<Void, Task<Void>>() {
                    @Override
                    public Task<Void> then(Task<Void> task) {
                        user.setIsCurrentUser(true);
                        return user.synchronizeAllAuthDataAsync();
                    }
                }).onSuccessTask(new Continuation<Void, Task<Void>>() {
                    @Override
                    public Task<Void> then(Task<Void> task) {
                        return store.setAsync(user).continueWith(new Continuation<Void, Void>() {
                            @Override
                            public Void then(Task<Void> task) {
                                synchronized (mutex) {
                                    currentUserMatchesDisk = !task.isFaulted();
                                    currentUser = user;
                                }
                                return null;
                            }
                        });
                    }
                });
            }
        });
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

        return taskQueue.enqueue(new Continuation<Void, Task<Boolean>>() {
            @Override
            public Task<Boolean> then(Task<Void> toAwait) {
                return toAwait.continueWithTask(new Continuation<Void, Task<Boolean>>() {
                    @Override
                    public Task<Boolean> then(Task<Void> task) {
                        return store.existsAsync();
                    }
                });
            }
        });
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
        return getAsync(false).onSuccess(new Continuation<ParseUser, String>() {
            @Override
            public String then(Task<ParseUser> task) {
                ParseUser user = task.getResult();
                return user != null ? user.getSessionToken() : null;
            }
        });
    }

    @Override
    public Task<Void> logOutAsync() {
        return taskQueue.enqueue(new Continuation<Void, Task<Void>>() {
            @Override
            public Task<Void> then(Task<Void> toAwait) {
                // We can parallelize disk and network work, but only after we restore the current user from
                // disk.
                final Task<ParseUser> userTask = getAsync(false);
                return Task.whenAll(Arrays.asList(userTask, toAwait)).continueWithTask(new Continuation<Void, Task<Void>>() {
                    @Override
                    public Task<Void> then(Task<Void> task) {
                        Task<Void> logOutTask = userTask.onSuccessTask(new Continuation<ParseUser, Task<Void>>() {
                            @Override
                            public Task<Void> then(Task<ParseUser> task) {
                                ParseUser user = task.getResult();
                                if (user == null) {
                                    return task.cast();
                                }
                                return user.logOutAsync();
                            }
                        });

                        Task<Void> diskTask = store.deleteAsync().continueWith(new Continuation<Void, Void>() {
                            @Override
                            public Void then(Task<Void> task) {
                                boolean deleted = !task.isFaulted();
                                synchronized (mutex) {
                                    currentUserMatchesDisk = deleted;
                                    currentUser = null;
                                }
                                return null;
                            }
                        });
                        return Task.whenAll(Arrays.asList(logOutTask, diskTask));
                    }
                });
            }
        });
    }

    @Override
    public Task<ParseUser> getAsync(final boolean shouldAutoCreateUser) {
        synchronized (mutex) {
            if (currentUser != null) {
                return Task.forResult(currentUser);
            }
        }

        return taskQueue.enqueue(new Continuation<Void, Task<ParseUser>>() {
            @Override
            public Task<ParseUser> then(Task<Void> toAwait) {
                return toAwait.continueWithTask(new Continuation<Void, Task<ParseUser>>() {
                    @Override
                    public Task<ParseUser> then(Task<Void> ignored) {
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

                        return store.getAsync().continueWith(new Continuation<ParseUser, ParseUser>() {
                            @Override
                            public ParseUser then(Task<ParseUser> task) {
                                ParseUser current = task.getResult();
                                boolean matchesDisk = !task.isFaulted();

                                synchronized (mutex) {
                                    currentUser = current;
                                    currentUserMatchesDisk = matchesDisk;
                                }

                                if (current != null) {
                                    synchronized (current.mutex) {
                                        current.setIsCurrentUser(true);
                                    }
                                    return current;
                                }

                                if (shouldAutoCreateUser) {
                                    return lazyLogIn();
                                }
                                return null;
                            }
                        });
                    }
                });
            }
        });
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

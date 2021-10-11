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

class CachedCurrentInstallationController implements ParseCurrentInstallationController {

    /* package */ static final String TAG = "com.parse.CachedCurrentInstallationController";

    /*
     * Note about lock ordering:
     *
     * You must NOT acquire the ParseInstallation instance mutex (the "mutex" field in ParseObject)
     * while holding this current installation lock. (We used to use the ParseInstallation.class lock,
     * but moved on to an explicit lock object since anyone could acquire the ParseInstallation.class
     * lock as ParseInstallation is a public class.) Acquiring the instance mutex while holding this
     * current installation lock will lead to a deadlock.
     */
    private final Object mutex = new Object();

    private final TaskQueue taskQueue = new TaskQueue();

    private final ParseObjectStore<ParseInstallation> store;
    private final InstallationId installationId;

    // The "current installation" is the installation for this device. Protected by
    // mutex.
    /* package for test */ ParseInstallation currentInstallation;

    public CachedCurrentInstallationController(
            ParseObjectStore<ParseInstallation> store, InstallationId installationId) {
        this.store = store;
        this.installationId = installationId;
    }

    @Override
    public Task<Void> setAsync(final ParseInstallation installation) {
        if (!isCurrent(installation)) {
            return Task.forResult(null);
        }

        return taskQueue.enqueue(
                toAwait ->
                        toAwait.continueWithTask(task -> store.setAsync(installation))
                                .continueWithTask(
                                        task -> {
                                            installationId.set(installation.getInstallationId());
                                            return task;
                                        },
                                        ParseExecutors.io()));
    }

    @Override
    public Task<ParseInstallation> getAsync() {
        synchronized (mutex) {
            if (currentInstallation != null) {
                return Task.forResult(currentInstallation);
            }
        }

        return taskQueue.enqueue(
                toAwait ->
                        toAwait.continueWithTask(
                                task -> {
                                    synchronized (mutex) {
                                        if (currentInstallation != null) {
                                            return Task.forResult(currentInstallation);
                                        }
                                    }

                                    return store.getAsync()
                                            .continueWith(
                                                    task1 -> {
                                                        ParseInstallation current =
                                                                task1.getResult();
                                                        if (current == null) {
                                                            current =
                                                                    ParseObject.create(
                                                                            ParseInstallation
                                                                                    .class);
                                                            current.updateDeviceInfo(
                                                                    installationId);
                                                        } else {
                                                            installationId.set(
                                                                    current.getInstallationId());
                                                            PLog.v(
                                                                    TAG,
                                                                    "Successfully deserialized Installation object");
                                                        }

                                                        synchronized (mutex) {
                                                            currentInstallation = current;
                                                        }
                                                        return current;
                                                    },
                                                    ParseExecutors.io());
                                }));
    }

    @Override
    public Task<Boolean> existsAsync() {
        synchronized (mutex) {
            if (currentInstallation != null) {
                return Task.forResult(true);
            }
        }

        return taskQueue.enqueue(toAwait -> toAwait.continueWithTask(task -> store.existsAsync()));
    }

    @Override
    public void clearFromMemory() {
        synchronized (mutex) {
            currentInstallation = null;
        }
    }

    @Override
    public void clearFromDisk() {
        synchronized (mutex) {
            currentInstallation = null;
        }
        try {
            installationId.clear();
            ParseTaskUtils.wait(store.deleteAsync());
        } catch (ParseException e) {
            // ignored
        }
    }

    @Override
    public boolean isCurrent(ParseInstallation installation) {
        synchronized (mutex) {
            return currentInstallation == installation;
        }
    }
}

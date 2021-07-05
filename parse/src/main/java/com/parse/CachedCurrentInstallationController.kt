/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse

import com.parse.PLog.v
import com.parse.boltsinternal.Task

internal class CachedCurrentInstallationController(
    private val store: ParseObjectStore<ParseInstallation>,
    private val installationId: InstallationId
) : ParseCurrentInstallationController {
    /*
     * Note about lock ordering:
     *
     * You must NOT acquire the ParseInstallation instance mutex (the "mutex" field in ParseObject)
     * while holding this current installation lock. (We used to use the ParseInstallation.class lock,
     * but moved on to an explicit lock object since anyone could acquire the ParseInstallation.class
     * lock as ParseInstallation is a public class.) Acquiring the instance mutex while holding this
     * current installation lock will lead to a deadlock.
     */
    private val mutex = Any()
    private val taskQueue = TaskQueue()

    // The "current installation" is the installation for this device. Protected by
    // mutex.
    /* package for test */
    @JvmField
    var currentInstallation: ParseInstallation? = null

    override fun setAsync(installation: ParseInstallation): Task<Void?> {
        return if (!isCurrent(installation)) {
            Task.forResult(null)
        } else taskQueue.enqueue { toAwait: Task<Void?> ->
            toAwait.continueWithTask { store.setAsync(installation) }
                .continueWithTask(
                    { task: Task<Void?> ->
                        installationId.set(installation.installationId!!)
                        task
                    }, ParseExecutors.io()
                )
        }
    }

    override fun getAsync(): Task<ParseInstallation?> {
        synchronized(mutex) {
            if (currentInstallation != null) {
                return Task.forResult(currentInstallation)
            }
        }
        return taskQueue.enqueue { toAwait: Task<Void?> ->
            toAwait.continueWithTask {
                synchronized(mutex) {
                    if (currentInstallation != null) {
                        return@continueWithTask Task.forResult(currentInstallation!!)
                    }
                }
                store.getAsync.continueWith({ task1: Task<ParseInstallation> ->
                    var current = task1.result
                    if (current == null) {
                        current = ParseObject.create(ParseInstallation::class.java)
                        current.updateDeviceInfo(installationId)
                    } else {
                        installationId.set(current.installationId!!)
                        v(TAG, "Successfully deserialized Installation object")
                    }
                    synchronized(mutex) { currentInstallation = current }
                    current
                }, ParseExecutors.io())
            }
        }
    }

    override fun existsAsync(): Task<Boolean?> {
        synchronized(mutex) {
            if (currentInstallation != null) {
                return Task.forResult(true)
            }
        }
        return taskQueue.enqueue { toAwait: Task<Void?> -> toAwait.continueWithTask { store.existsAsync() } }
    }

    override fun clearFromMemory() {
        synchronized(mutex) { currentInstallation = null }
    }

    override fun clearFromDisk() {
        synchronized(mutex) { currentInstallation = null }
        try {
            installationId.clear()
            ParseTaskUtils.wait(store.deleteAsync())
        } catch (e: ParseException) {
            // ignored
        }
    }

    override fun isCurrent(installation: ParseInstallation): Boolean {
        synchronized(mutex) { return currentInstallation === installation }
    }

    companion object {
        /* package */
        const val TAG = "com.parse.CachedCurrentInstallationController"
    }
}
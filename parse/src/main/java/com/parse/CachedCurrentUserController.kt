/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse

import com.parse.boltsinternal.Task
import java.util.*

internal class CachedCurrentUserController(private val store: ParseObjectStore<ParseUser>) :
    ParseCurrentUserController {
    /**
     * Lock used to synchronize current user modifications and access.
     *
     *
     * Note about lock ordering:
     *
     *
     * You must NOT acquire the ParseUser instance mutex (the "mutex" field in ParseObject) while
     * holding this static initialization lock. Doing so will cause a deadlock.
     */
    private val mutex = Any()
    private val taskQueue = TaskQueue()

    /* package */
    @JvmField
    var currentUser: ParseUser? = null

    // Whether currentUser is known to match the serialized version on disk. This is useful for saving
    // a filesystem check if you try to load currentUser frequently while there is none on disk.
    /* package */
    @JvmField
    var currentUserMatchesDisk = false
    override fun setAsync(user: ParseUser): Task<Void?> {
        return taskQueue.enqueue { toAwait: Task<Void?> ->
            toAwait.continueWithTask { task: Task<Void?> ->
                var oldCurrentUser: ParseUser?
                synchronized(mutex) { oldCurrentUser = currentUser }
                if (oldCurrentUser != null && oldCurrentUser !== user) {
                    // We don't need to revoke the token since we're not explicitly calling logOut
                    // We don't need to remove persisted files since we're overwriting them
                    return@continueWithTask oldCurrentUser!!.logOutAsync(false)
                        .continueWith<Void> {
                            null // ignore errors
                        }
                }
                task
            }.onSuccessTask {
                user.setIsCurrentUser(true)
                user.synchronizeAllAuthDataAsync()
            }.onSuccessTask {
                store.setAsync(user).continueWith { task: Task<Void> ->
                    synchronized(mutex) {
                        currentUserMatchesDisk = !task.isFaulted
                        currentUser = user
                    }
                    null
                }
            }
        }
    }

    override fun setIfNeededAsync(user: ParseUser): Task<Void?> {
        synchronized(mutex) {
            if (!user.isCurrentUser || currentUserMatchesDisk) {
                return Task.forResult(null)
            }
        }
        return setAsync(user)
    }

    override fun getAsync(): Task<ParseUser?> {
        return getAsync(ParseUser.isAutomaticUserEnabled())
    }

    override fun existsAsync(): Task<Boolean?> {
        synchronized(mutex) {
            if (currentUser != null) {
                return Task.forResult(true)
            }
        }
        return taskQueue.enqueue { toAwait: Task<Void?> -> toAwait.continueWithTask { store.existsAsync() } }
    }

    override fun isCurrent(user: ParseUser): Boolean {
        synchronized(mutex) { return currentUser === user }
    }

    override fun clearFromMemory() {
        synchronized(mutex) {
            currentUser = null
            currentUserMatchesDisk = false
        }
    }

    override fun clearFromDisk() {
        synchronized(mutex) {
            currentUser = null
            currentUserMatchesDisk = false
        }
        try {
            ParseTaskUtils.wait(store.deleteAsync())
        } catch (e: ParseException) {
            // ignored
        }
    }

    override fun getCurrentSessionTokenAsync(): Task<String> {
        return getAsync(false).onSuccess { task: Task<ParseUser?> ->
            val user = task.result
            user?.sessionToken
        }
    }

    override fun logOutAsync(): Task<Void?> {
        return taskQueue.enqueue { toAwait: Task<Void?>? ->
            // We can parallelize disk and network work, but only after we restore the current user from
            // disk.
            val userTask = getAsync(false)
            Task.whenAll(listOf(userTask, toAwait)).continueWithTask {
                val logOutTask = userTask.onSuccessTask { task1: Task<ParseUser?> ->
                    val user = task1.result ?: return@onSuccessTask task1.cast<Void>()
                    user.logOutAsync()
                }
                val diskTask = store.deleteAsync().continueWith<Void?> { task: Task<Void?> ->
                    val deleted = !task.isFaulted
                    synchronized(mutex) {
                        currentUserMatchesDisk = deleted
                        currentUser = null
                    }
                    null
                }
                Task.whenAll(listOf(logOutTask, diskTask))
            }
        }
    }

    override fun getAsync(shouldAutoCreateUser: Boolean): Task<ParseUser?> {
        synchronized(mutex) {
            if (currentUser != null) {
                return Task.forResult(currentUser)
            }
        }
        return taskQueue.enqueue { toAwait: Task<Void?> ->
            toAwait.continueWithTask {
                var current: ParseUser?
                var matchesDisk: Boolean
                synchronized(mutex) {
                    current = currentUser
                    matchesDisk = currentUserMatchesDisk
                }
                if (current != null) {
                    return@continueWithTask Task.forResult(current)
                }
                if (matchesDisk) {
                    if (shouldAutoCreateUser) {
                        return@continueWithTask Task.forResult(lazyLogIn())
                    }
                    return@continueWithTask null
                }
                store.getAsync.continueWith { task: Task<ParseUser> ->
                    val current1 = task.result
                    val matchesDisk1 = !task.isFaulted
                    synchronized(mutex) {
                        currentUser = current1
                        currentUserMatchesDisk = matchesDisk1
                    }
                    if (current1 != null) {
                        synchronized(current1.mutex) { current1.setIsCurrentUser(true) }
                        return@continueWith current1
                    }
                    if (shouldAutoCreateUser) {
                        return@continueWith lazyLogIn()
                    }
                    null
                }
            }
        }
    }

    private fun lazyLogIn(): ParseUser {
        val authData = ParseAnonymousUtils.getAuthData()
        return lazyLogIn(ParseAnonymousUtils.AUTH_TYPE, authData)
    }

    /* package for tests */
    fun lazyLogIn(authType: String?, authData: Map<String?, String?>?): ParseUser {
        // Note: if authType != ParseAnonymousUtils.AUTH_TYPE the user is not "lazy".
        val user = ParseObject.create(ParseUser::class.java)
        synchronized(user.mutex) {
            user.setIsCurrentUser(true)
            user.putAuthData(authType, authData)
        }
        synchronized(mutex) {
            currentUserMatchesDisk = false
            currentUser = user
        }
        return user
    }
}
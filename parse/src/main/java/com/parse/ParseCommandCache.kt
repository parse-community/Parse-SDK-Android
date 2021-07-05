/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import com.parse.ConnectivityNotifier.Companion.getNotifier
import com.parse.ConnectivityNotifier.Companion.isConnected
import com.parse.ConnectivityNotifier.ConnectivityListener
import com.parse.boltsinternal.Capture
import com.parse.boltsinternal.Continuation
import com.parse.boltsinternal.Task
import com.parse.boltsinternal.TaskCompletionSource
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.charset.Charset
import java.util.*
import java.util.logging.Level
import java.util.logging.Logger


@Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
private fun Any.wait() = (this as Object).wait()
private fun Any.wait(timeout: Long) = (this as Object).wait(timeout)
private fun Any.notifyAll() = (this as Object).notifyAll()
private fun Any.notify() = (this as Object).notify()

/**
 * ParseCommandCache manages an on-disk cache of commands to be executed, and a thread with a
 * standard run loop that executes the commands. There should only ever be one instance of this
 * class, because multiple instances would be running separate threads trying to read and execute
 * the same commands.
 */
internal class ParseCommandCache(context: Context?, client: ParseHttpClient) :
    ParseEventuallyQueue() {
    private val listener: ConnectivityListener = object : ConnectivityListener {
        override fun networkConnectivityStatusChanged(context: Context?, intent: Intent?) {
            val connectionLost = intent?.getBooleanExtra(
                ConnectivityManager.EXTRA_NO_CONNECTIVITY, false
            ) ?: false
            val isConnected = isConnected(context!!)

            /*
       Hack to avoid blocking the UI thread with disk I/O

       setConnected uses the same lock we use for synchronizing disk I/O, so there's a possibility
       that we can block the UI thread on disk I/O, so we're going to bump the lock usage to a
       different thread.

       TODO(grantland): Convert to TaskQueue, similar to ParsePinningEventuallyQueue
        */Task.call({
                if (connectionLost) {
                    this@ParseCommandCache.isConnected = false
                } else {
                    this@ParseCommandCache.isConnected = connectionLost
                }
                null
            }, ParseExecutors.io())
        }
    }

    // Guards access to running. Gets a broadcast whenever running changes. A thread should only wait
    // on runningLock if it's sure the value of running is going to change. Only the run loop
    // (runLoop) thread should ever notify on runningLock. It's perfectly fine for a thread that has
    // runningLock to then also try to acquire the other lock.
    private val runningLock: Any
    private val httpClient: ParseHttpClient
    private val cachePath // Where the cache is stored on disk.
            : File

    // Map of filename to TaskCompletionSource, for all commands that are in the queue from this run
    // of the program. This is necessary so that the original objects can be notified after their
    // saves complete.
    private val pendingTasks = HashMap<File, TaskCompletionSource<JSONObject>>()
    private val log // Why is there a custom logger? To prevent Mockito deadlock!
            : Logger
    private lateinit var notifier: ConnectivityNotifier
    private var timeoutMaxRetries =
        5 // Don't retry more than 5 times before assuming disconnection.
    private var timeoutRetryWaitSeconds = 600.0 // Wait 10 minutes before retrying after network

    // timeout.
    private var maxCacheSizeBytes = 10 * 1024 * 1024 // Don't consume more than N bytes of storage.

    // processed by the run loop?
    private var shouldStop // Should the run loop thread processing the disk cache continue?
            : Boolean
    private var unprocessedCommandsExist // Has a command been added which hasn't yet been
            = false
    private var running // Is the run loop executing commands from the disk cache running?
            : Boolean

    override fun onDestroy() {
        //TODO (grantland): pause #6484855
        notifier.removeListener(listener)
    }

    // Set the maximum number of times to retry before assuming disconnection.
    fun setTimeoutMaxRetries(tries: Int) {
        synchronized(lock) { timeoutMaxRetries = tries }
    }

    // Sets the amount of time to wait before retrying after network timeout.
    override fun setTimeoutRetryWaitSeconds(seconds: Double) {
        synchronized(lock) { timeoutRetryWaitSeconds = seconds }
    }

    // Sets the maximum amount of storage space this cache can consume.
    override fun setMaxCacheSizeBytes(bytes: Int) {
        synchronized(lock) { maxCacheSizeBytes = bytes }
    }

    // Starts the run loop thread running.
    override fun resume() {
        synchronized(runningLock) {
            if (!running) {
                object : Thread("ParseCommandCache.runLoop()") {
                    override fun run() {
                        runLoop()
                    }
                }.start()
                try {
                    runningLock.wait()
                } catch (e: InterruptedException) {
                    // Someone told this thread to stop.
                    synchronized(lock) {
                        shouldStop = true
                        lock.notifyAll()
                    }
                }
            }
        }
    }

    // Stops the run loop thread from processing commands until resume is called.
    // When this function returns, the run loop has stopped.
    override fun pause() {
        synchronized(runningLock) {
            if (running) {
                synchronized(lock) {
                    shouldStop = true
                    lock.notifyAll()
                }
            }
            while (running) {
                try {
                    runningLock.wait()
                } catch (e: InterruptedException) {
                    // Someone told this thread to stop while it was already waiting to
                    // finish...
                    // Ignore them and continue waiting.
                }
            }
        }
    }

    /**
     * Removes a file from the file system and any internal caches.
     */
    private fun removeFile(file: File) {
        synchronized(lock) {

            // Remove the data in memory for this command.
            pendingTasks.remove(file)

            // Release all the localIds referenced by the command.
            // Read one command from the cache.
            val json: JSONObject
            try {
                json = ParseFileUtils.readFileToJSONObject(file)
                val command = commandFromJSON(json)
                command?.releaseLocalIds()
            } catch (e: Exception) {
                // Well, we did our best. We'll just have to leak a localId.
            }

            // Delete the command file itself.
            ParseFileUtils.deleteQuietly(file)
        }
    }

    /**
     * Makes this command cache forget all the state it keeps during a single run of the app. This is
     * only for testing purposes.
     */
    public override fun simulateReboot() {
        synchronized(lock) { pendingTasks.clear() }
    }

    /**
     * Fakes an object update notification for use in tests. This is used by saveEventually to make it
     * look like test code has updated an object through the command cache even if it actually
     * avoided executing update by determining the object wasn't dirty.
     */
    public override fun fakeObjectUpdate() {
        notifyTestHelper(TestHelper.COMMAND_ENQUEUED)
        notifyTestHelper(TestHelper.COMMAND_SUCCESSFUL)
        notifyTestHelper(TestHelper.OBJECT_UPDATED)
    }

    override fun enqueueEventuallyAsync(
        command: ParseRESTCommand,
        `object`: ParseObject?
    ): Task<JSONObject> {
        return enqueueEventuallyAsync(command, false, `object`)
    }

    /**
     * Attempts to run the given command and any pending commands. Adds the command to the pending set
     * if it can't be run yet.
     *
     * @param command      - The command to run.
     * @param preferOldest - When the disk is full, if preferOldest, drop new commands. Otherwise, the oldest
     * commands will be deleted to make room.
     * @param object       - See runEventually.
     */
    private fun enqueueEventuallyAsync(
        command: ParseRESTCommand, preferOldest: Boolean,
        `object`: ParseObject?
    ): Task<JSONObject> {
        Parse.requirePermission(Manifest.permission.ACCESS_NETWORK_STATE)
        val tcs = TaskCompletionSource<JSONObject>()
        val json: ByteArray
        // If this object doesn't have an objectId yet, store the localId so we can remap it to the
        // objectId after the save completes.
        if (`object` != null && `object`.objectId == null) {
            command.localId = `object`.getOrCreateLocalId()
        }
        val jsonObject = command.toJSONObject()
        json = jsonObject.toString().toByteArray(Charset.forName("UTF-8"))

        // If this object by itself is larger than the full disk cache, then don't
        // even bother trying.
        if (json.size > maxCacheSizeBytes) {
            if (Parse.LOG_LEVEL_WARNING >= Parse.getLogLevel()) {
                log.warning("Unable to save command for later because it's too big.")
            }
            notifyTestHelper(TestHelper.COMMAND_NOT_ENQUEUED)
            return Task.forResult(null)
        }
        synchronized(lock) {
            try {
                // Is there enough free storage space?
                val fileNames = cachePath.list()
                if (fileNames != null) {
                    Arrays.sort(fileNames)
                    var size = 0
                    for (fileName in fileNames) {
                        val file = File(cachePath, fileName)
                        // Should be safe to convert long to int, because we don't allow
                        // files larger than 2GB.
                        size += file.length().toInt()
                    }
                    size += json.size
                    if (size > maxCacheSizeBytes) {
                        if (preferOldest) {
                            if (Parse.LOG_LEVEL_WARNING >= Parse.getLogLevel()) {
                                log.warning("Unable to save command for later because storage is full.")
                            }
                            return Task.forResult(null)
                        } else {
                            if (Parse.LOG_LEVEL_WARNING >= Parse.getLogLevel()) {
                                log.warning("Deleting old commands to make room in command cache.")
                            }
                            var indexToDelete = 0
                            while (size > maxCacheSizeBytes && indexToDelete < fileNames.size) {
                                val file = File(cachePath, fileNames[indexToDelete++])
                                size -= file.length().toInt()
                                removeFile(file)
                            }
                        }
                    }
                }

                // Get the current time to store in the filename, so that we process them in order.
                var prefix1 = java.lang.Long.toHexString(System.currentTimeMillis())
                if (prefix1.length < 16) {
                    val zeroes = CharArray(16 - prefix1.length)
                    Arrays.fill(zeroes, '0')
                    prefix1 = String(zeroes) + prefix1
                }

                // Then add another incrementing number in case we enqueue items faster than the system's
                // time granularity.
                var prefix2 = Integer.toHexString(filenameCounter++)
                if (prefix2.length < 8) {
                    val zeroes = CharArray(8 - prefix2.length)
                    Arrays.fill(zeroes, '0')
                    prefix2 = String(zeroes) + prefix2
                }
                val prefix = "CachedCommand_" + prefix1 + "_" + prefix2 + "_"

                // Get a unique filename to store this command in.
                val path = File.createTempFile(prefix, "", cachePath)

                // Write the command to that file.
                pendingTasks[path] = tcs
                command.retainLocalIds()
                ParseFileUtils.writeByteArrayToFile(path, json)
                notifyTestHelper(TestHelper.COMMAND_ENQUEUED)
                unprocessedCommandsExist = true
            } catch (e: IOException) {
                if (Parse.LOG_LEVEL_WARNING >= Parse.getLogLevel()) {
                    log.log(Level.WARNING, "Unable to save command for later.", e)
                }
            } finally {
                lock.notifyAll()
            }
        }
        return tcs.task
    }

    /**
     * Returns the number of commands currently in the set waiting to be run.
     */
    override fun pendingCount(): Int {
        return pendingCount
    }

    /**
     * Gets rid of all pending commands.
     */
    override fun clear() {
        synchronized(lock) {
            val files = cachePath.listFiles() ?: return
            for (file in files) {
                removeFile(file)
            }
            pendingTasks.clear()
        }
    }

    override var isConnected: Boolean = false
        get() = super.isConnected
        set(value) {
            synchronized(lock) {
                if (isConnected != value) {
                    if (value) {
                        lock.notifyAll()
                    }
                }
                field = value
            }
        }

    /**
     * This is kind of like ParseTaskUtils.wait(), except that it gives up the CommandCache's lock
     * while the task is running, and reclaims it before returning.
     */
    @Throws(ParseException::class)
    private fun <T> waitForTaskWithoutLock(task: Task<T>): T {
        synchronized(lock) {
            val finished = Capture(false)
            task.continueWith(Continuation<T, Void?> { task1: Task<T>? ->
                finished.set(true)
                synchronized(lock) { lock.notifyAll() }
                null
            }, Task.BACKGROUND_EXECUTOR)
            while (!finished.get()) {
                try {
                    lock.wait()
                } catch (ie: InterruptedException) {
                    shouldStop = true
                }
            }
            return ParseTaskUtils.wait(task) // Just to get the return value and maybe rethrow.
        }
    }

    /**
     * Attempts to run every command in the disk queue in order, synchronously. If there is no network
     * connection, returns immediately without doing anything. If there is supposedly a connection,
     * but parse can't be reached, waits timeoutRetryWaitSeconds before retrying up to
     * retriesRemaining times. Blocks until either there's a connection, or the retries are exhausted.
     * If any command fails, just deletes it and moves on to the next one.
     */
    private fun maybeRunAllCommandsNow(retriesRemaining: Int) {
        synchronized(lock) {
            unprocessedCommandsExist = false
            if (!isConnected) {
                // There's no way to do work when there's no network connection.
                return
            }
            val fileNames = cachePath.list()
            if (fileNames == null || fileNames.isEmpty()) {
                return
            }
            Arrays.sort(fileNames)
            for (fileName in fileNames) {
                val file = File(cachePath, fileName)

                // Read one command from the cache.
                val json: JSONObject? = try {
                    ParseFileUtils.readFileToJSONObject(file)
                } catch (e: FileNotFoundException) {
                    // This shouldn't really be possible.
                    if (Parse.LOG_LEVEL_ERROR >= Parse.getLogLevel()) {
                        log.log(Level.SEVERE, "File disappeared from cache while being read.", e)
                    }
                    continue
                } catch (e: IOException) {
                    if (Parse.LOG_LEVEL_ERROR >= Parse.getLogLevel()) {
                        log.log(Level.SEVERE, "Unable to read contents of file in cache.", e)
                    }
                    removeFile(file)
                    continue
                } catch (e: JSONException) {
                    if (Parse.LOG_LEVEL_ERROR >= Parse.getLogLevel()) {
                        log.log(Level.SEVERE, "Error parsing JSON found in cache.", e)
                    }
                    removeFile(file)
                    continue
                }

                // Convert the command from a string.
                val tcs: TaskCompletionSource<JSONObject>? =
                    if (pendingTasks.containsKey(file)) pendingTasks[file] else null
                val command: ParseRESTCommand? = try {
                    commandFromJSON(json)
                } catch (e: JSONException) {
                    if (Parse.LOG_LEVEL_ERROR >= Parse.getLogLevel()) {
                        log.log(Level.SEVERE, "Unable to create ParseCommand from JSON.", e)
                    }
                    removeFile(file)
                    continue
                }
                try {
                    var commandTask: Task<JSONObject?>
                    if (command == null) {
                        commandTask = Task.forResult(null)
                        tcs?.setResult(null)
                        notifyTestHelper(TestHelper.COMMAND_OLD_FORMAT_DISCARDED)
                    } else {
                        commandTask = command.executeAsync(httpClient)
                            .continueWithTask { task: Task<JSONObject?> ->
                                val localId = command.localId
                                val error = task.error
                                if (error != null) {
                                    if (error is ParseException
                                        && error.code == ParseException.CONNECTION_FAILED
                                    ) {
                                        // do nothing
                                    } else {
                                        tcs?.setError(error)
                                    }
                                    return@continueWithTask task
                                }
                                val json1 = task.result
                                if (tcs != null) {
                                    tcs.setResult(json1)
                                } else if (localId != null) {
                                    // If this command created a new objectId, add it to the map.
                                    val objectId = json1!!.optString("objectId", null)
                                    if (objectId != null) {
                                        ParseCorePlugins.getInstance()
                                            .localIdManager.setObjectId(localId, objectId)
                                    }
                                }
                                task
                            }
                    }
                    waitForTaskWithoutLock(commandTask)
                    if (tcs != null) {
                        waitForTaskWithoutLock(tcs.task)
                    }

                    // The command succeeded. Remove it from the cache.
                    removeFile(file)
                    notifyTestHelper(TestHelper.COMMAND_SUCCESSFUL)
                } catch (e: ParseException) {
                    if (e.code == ParseException.CONNECTION_FAILED) {
                        if (retriesRemaining > 0) {
                            // Reachability says we have a network connection, but we can't actually contact
                            // Parse. Wait N minutes, or until we get signaled again before doing anything else.
                            if (Parse.LOG_LEVEL_INFO >= Parse.getLogLevel()) {
                                log.info(
                                    "Network timeout in command cache. Waiting for " + timeoutRetryWaitSeconds
                                            + " seconds and then retrying " + retriesRemaining + " times."
                                )
                            }
                            var currentTime = System.currentTimeMillis()
                            val waitUntil = currentTime + (timeoutRetryWaitSeconds * 1000).toLong()
                            while (currentTime < waitUntil) {
                                // We haven't waited long enough, but if we lost the connection,
                                // or should stop, just quit.
                                if (!isConnected || shouldStop) {
                                    if (Parse.LOG_LEVEL_INFO >= Parse.getLogLevel()) {
                                        log.info("Aborting wait because runEventually thread should stop.")
                                    }
                                    return
                                }
                                try {
                                    lock.wait(waitUntil - currentTime)
                                } catch (ie: InterruptedException) {
                                    shouldStop = true
                                }
                                currentTime = System.currentTimeMillis()
                                if (currentTime < waitUntil - (timeoutRetryWaitSeconds * 1000).toLong()) {
                                    // This situation should be impossible, so it must mean the clock changed.
                                    currentTime =
                                        waitUntil - (timeoutRetryWaitSeconds * 1000).toLong()
                                }
                            }
                            maybeRunAllCommandsNow(retriesRemaining - 1)
                        } else {
                            isConnected = false
                            notifyTestHelper(TestHelper.NETWORK_DOWN)
                        }
                    } else {
                        if (Parse.LOG_LEVEL_ERROR >= Parse.getLogLevel()) {
                            log.log(Level.SEVERE, "Failed to run command.", e)
                        }
                        // Delete the command from the cache, even though it failed.
                        // Otherwise, we'll just keep trying it forever.
                        removeFile(file)
                        notifyTestHelper(TestHelper.COMMAND_FAILED, e)
                    }
                }
            }
        }
    }

    /**
     * The main function of the run loop thread. This function basically loops forever (unless pause
     * is called). On each iteration, if it hasn't been told to stop, it calls maybeRunAllCommandsNow
     * to try to execute everything queued up on disk. Then it waits until it gets signaled again by
     * lock.notify(). Usually that happens as a result of either (1) Parse being initialized, (2)
     * runEventually being called, or (3) the OS notifying that the network connection has been
     * re-established.
     */
    private fun runLoop() {
        if (Parse.LOG_LEVEL_INFO >= Parse.getLogLevel()) {
            log.info("Parse command cache has started processing queued commands.")
        }
        // Make sure we marked as running.
        synchronized(runningLock) {
            if (running) {
                // Don't run this thread more than once.
                return
            } else {
                running = true
                runningLock.notifyAll()
            }
        }
        var shouldRun: Boolean
        synchronized(lock) { shouldRun = !(shouldStop || Thread.interrupted()) }
        while (shouldRun) {
            synchronized(lock) {
                try {
                    maybeRunAllCommandsNow(timeoutMaxRetries)
                    if (!shouldStop) {
                        try {
                            /*
                             * If an unprocessed command was added, avoid waiting because we want
                             * maybeRunAllCommandsNow to run at least once to potentially process that command.
                             */
                            if (!unprocessedCommandsExist) {
                                lock.wait()
                            }
                        } catch (e: InterruptedException) {
                            shouldStop = true
                        }
                    }
                } catch (e: Exception) {
                    if (Parse.LOG_LEVEL_ERROR >= Parse.getLogLevel()) {
                        log.log(Level.SEVERE, "saveEventually thread had an error.", e)
                    }
                } finally {
                    shouldRun = !shouldStop
                }
            }
        }
        synchronized(runningLock) {
            running = false
            runningLock.notifyAll()
        }
        if (Parse.LOG_LEVEL_INFO >= Parse.getLogLevel()) {
            log.info("saveEventually thread has stopped processing commands.")
        }
    }

    companion object {
        private const val TAG = "com.parse.ParseCommandCache"

        // Lock guards access to the file system and all of the instance variables above. It is static so
        // that if somehow there are two instances of ParseCommandCache, they won't step on each others'
        // toes while using the file system. A thread with lock should *not* try to get runningLock, under
        // penalty of deadlock. Only the run loop (runLoop) thread should ever wait on this lock. Other
        // threads should notify on this lock whenever the run loop should wake up and try to execute more
        // commands.
        private val lock = Any()

        // order.
        private var filenameCounter = 0 // Appended to temp file names so we know their creation

        // Construct the path to the cache directory.
        private val cacheDir: File
            get() {
                // Construct the path to the cache directory.
                val cacheDir = File(Parse.getParseDir(), "CommandCache")
                cacheDir.mkdirs()
                return cacheDir
            }
        @JvmStatic
        val pendingCount: Int
            get() {
                synchronized(lock) {
                    val files = cacheDir.list()
                    return files?.size ?: 0
                }
            }
    }

    init {
        isConnected = false
        shouldStop = false
        running = false
        runningLock = Any()
        httpClient = client
        log = Logger.getLogger(TAG)
        cachePath = cacheDir

        if (Parse.hasPermission(Manifest.permission.ACCESS_NETWORK_STATE)) {
            // The command cache only works if the user has granted us permission to monitor the network.
            isConnected = isConnected(context!!)
            notifier = getNotifier(context)
            notifier.addListener(listener)
            resume()
        }
    }
}
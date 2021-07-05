/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse

import android.util.SparseArray
import com.parse.ParseRESTCommand.Companion.fromJSONObject
import com.parse.ParseRESTCommand.Companion.isValidCommandJSONObject
import com.parse.ParseRESTCommand.Companion.isValidOldFormatCommandJSONObject
import com.parse.boltsinternal.Task
import org.json.JSONException
import org.json.JSONObject
import java.util.*
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

/* package */
internal abstract class ParseEventuallyQueue {
    open var isConnected = false

    /**
     * Gets notifications of various events happening in the command cache, so that tests can be
     * synchronized.
     */
    private val testHelper = TestHelper()
    abstract fun onDestroy()
    abstract fun pendingCount(): Int
    open fun setTimeoutRetryWaitSeconds(seconds: Double) {
        // do nothing
    }

    open fun setMaxCacheSizeBytes(bytes: Int) {
        // do nothing
    }

    internal fun notifyTestHelper(event: Int, t: Throwable? = null) {
        testHelper.notify(event, t)
    }

    abstract fun pause()
    abstract fun resume()

    /**
     * Attempts to run the given command and any pending commands. Adds the command to the pending set
     * if it can't be run yet.
     *
     * @param command - The command to run.
     * @param object  - If this command references an unsaved object, we need to remove any previous command
     * referencing that unsaved object. Otherwise, it will get created over and over again.
     * So object is a reference to the object, if it has no objectId. Otherwise, it can be
     * null.
     */
    abstract fun enqueueEventuallyAsync(
        command: ParseRESTCommand,
        `object`: ParseObject?
    ): Task<JSONObject>

    @Throws(JSONException::class)
    protected fun commandFromJSON(json: JSONObject?): ParseRESTCommand? {
        var command: ParseRESTCommand? = null

        json?.let {
            when {
                isValidCommandJSONObject(json) -> {
                    command = fromJSONObject(json)
                }
                isValidOldFormatCommandJSONObject(json) -> {
                    // do nothing
                }
                else -> {
                    throw JSONException("Failed to load command from JSON.")
                }
            }
        }

        return command
    }

    /* package */
    open fun waitForOperationSetAndEventuallyPin(
        operationSet: ParseOperationSet?,
        eventuallyPin: EventuallyPin?
    ): Task<JSONObject?>? {
        return Task.forResult(null)
    }

    /* package */
    abstract fun simulateReboot()

    /**
     * Gets rid of all pending commands.
     */
    abstract fun clear()

    /**
     * Fakes an object update notification for use in tests. This is used by saveEventually to make it
     * look like test code has updated an object through the command cache even if it actually
     * avoided executing update by determining the object wasn't dirty.
     */
    open fun fakeObjectUpdate() {
        testHelper.notify(TestHelper.COMMAND_ENQUEUED)
        testHelper.notify(TestHelper.COMMAND_SUCCESSFUL)
        testHelper.notify(TestHelper.OBJECT_UPDATED)
    }

    /**
     * Gets notifications of various events happening in the command cache, so that tests can be
     * synchronized. See ParseCommandCacheTest for examples of how to use this.
     */
    class TestHelper {
        private val events = SparseArray<Semaphore>()
        fun clear() {
            events.clear()
            events.put(COMMAND_SUCCESSFUL, Semaphore(MAX_EVENTS))
            events.put(COMMAND_FAILED, Semaphore(MAX_EVENTS))
            events.put(COMMAND_ENQUEUED, Semaphore(MAX_EVENTS))
            events.put(COMMAND_NOT_ENQUEUED, Semaphore(MAX_EVENTS))
            events.put(OBJECT_UPDATED, Semaphore(MAX_EVENTS))
            events.put(OBJECT_REMOVED, Semaphore(MAX_EVENTS))
            events.put(NETWORK_DOWN, Semaphore(MAX_EVENTS))
            events.put(COMMAND_OLD_FORMAT_DISCARDED, Semaphore(MAX_EVENTS))
            for (i in 0 until events.size()) {
                val event = events.keyAt(i)
                events[event].acquireUninterruptibly(MAX_EVENTS)
            }
        }

        fun unexpectedEvents(): Int {
            var sum = 0
            for (i in 0 until events.size()) {
                val event = events.keyAt(i)
                sum += events[event].availablePermits()
            }
            return sum
        }

        val unexpectedEvents: List<String>
            get() {
                val unexpectedEvents: MutableList<String> = ArrayList()
                for (i in 0 until events.size()) {
                    val event = events.keyAt(i)
                    if (events[event].availablePermits() > 0) {
                        unexpectedEvents.add(getEventString(event))
                    }
                }
                return unexpectedEvents
            }

        @JvmOverloads
        fun notify(event: Int, t: Throwable? = null) {
            events[event].release()
        }

        @JvmOverloads
        fun waitFor(event: Int, permits: Int = 1): Boolean {
            return try {
                events[event].tryAcquire(permits, 10, TimeUnit.SECONDS)
            } catch (e: InterruptedException) {
                e.printStackTrace()
                false
            }
        }

        companion object {
            const val COMMAND_SUCCESSFUL = 1
            const val COMMAND_FAILED = 2
            const val COMMAND_ENQUEUED = 3
            const val COMMAND_NOT_ENQUEUED = 4
            const val OBJECT_UPDATED = 5
            const val OBJECT_REMOVED = 6
            const val NETWORK_DOWN = 7
            const val COMMAND_OLD_FORMAT_DISCARDED = 8
            private const val MAX_EVENTS = 1000
            fun getEventString(event: Int): String {
                return when (event) {
                    COMMAND_SUCCESSFUL -> "COMMAND_SUCCESSFUL"
                    COMMAND_FAILED -> "COMMAND_FAILED"
                    COMMAND_ENQUEUED -> "COMMAND_ENQUEUED"
                    COMMAND_NOT_ENQUEUED -> "COMMAND_NOT_ENQUEUED"
                    OBJECT_UPDATED -> "OBJECT_UPDATED"
                    OBJECT_REMOVED -> "OBJECT_REMOVED"
                    NETWORK_DOWN -> "NETWORK_DOWN"
                    COMMAND_OLD_FORMAT_DISCARDED -> "COMMAND_OLD_FORMAT_DISCARDED"
                    else -> throw IllegalStateException("Encountered unknown event: $event")
                }
            }
        }

        init {
            clear()
        }
    }
}
/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse

import com.parse.boltsinternal.Continuation
import com.parse.boltsinternal.Task
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock

/**
 * A helper class for enqueueing tasks
 */
class TaskQueue {
    val lock: Lock = ReentrantLock()

    /**
     * We only need to keep the tail of the queue. Cancelled tasks will just complete
     * normally/immediately when their turn arrives.
     */
    private var tail: Task<Void>? = null

    /**
     * Gets a task that can be safely awaited and is dependent on the current tail of the queue. This
     * essentially gives us a proxy for the tail end of the queue that can be safely cancelled.
     *
     * @return A new task that should be awaited by enqueued tasks.
     */
    private val taskToAwait: Task<Void?>
        get() {
            lock.lock()
            return try {
                val toAwait = if (tail != null) tail!! else Task.forResult<Void?>(null)
                toAwait.continueWith { null }
            } finally {
                lock.unlock()
            }
        }

    /**
     * Enqueues a task created by taskStart.
     *
     * @param taskStart A function given a task to await once state is snapshotted (e.g. after capturing
     * session tokens at the time of the save call). Awaiting this task will wait for the
     * created task's turn in the queue.
     * @return The task created by the taskStart function.
     */
    fun <T> enqueue(taskStart: Continuation<Void, Task<T?>>): Task<T?> {
        lock.lock()
        return try {
            val oldTail = if (tail != null) tail!! else Task.forResult<Void?>(null)
            // The task created by taskStart is responsible for waiting for the task passed into it before
            // doing its work (this gives it an opportunity to do startup work or save state before
            // waiting for its turn in the queue)
            val task: Task<T?> = try {
                val toAwait = taskToAwait
                taskStart.then(toAwait)
            } catch (e: RuntimeException) {
                throw e
            } catch (e: Exception) {
                throw RuntimeException(e)
            }

            // The tail task should be dependent on the old tail as well as the newly-created task. This
            // prevents cancellation of the new task from causing the queue to run out of order.
            tail = Task.whenAll(listOf(oldTail, task))
            task
        } finally {
            lock.unlock()
        }
    }

    @Throws(InterruptedException::class)
    fun waitUntilFinished() {
        lock.lock()
        try {
            if (tail == null) {
                return
            }
            tail!!.waitForCompletion()
        } finally {
            lock.unlock()
        }
    }

    companion object {
        /**
         * Creates a continuation that will wait for the given task to complete before running the next
         * continuations.
         */
        @JvmStatic
        fun <T> waitFor(toAwait: Task<Void?>): Continuation<T?, Task<T>> {
            return Continuation { task: Task<T>? -> toAwait.continueWithTask { task } }
        }
    }
}
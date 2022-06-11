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
import com.parse.boltsinternal.TaskCompletionSource;
import java.util.ArrayList;
import java.util.List;

/**
 * Helper class to step through a {@link TaskQueue}.
 *
 * <p>{@link #enqueue()} and {@link #dequeue()} works as FIFO list that enqueues an unresolved
 * {@link Task} to the end of the {@link TaskQueue} and resolves the first {@link Task}.
 */
public class TaskQueueTestHelper {

    private final Object lock = new Object();
    private final TaskQueue taskQueue;
    private final List<TaskCompletionSource<Void>> pendingTasks = new ArrayList<>();

    public TaskQueueTestHelper(TaskQueue taskQueue) {
        this.taskQueue = taskQueue;
    }

    /** Pauses the {@link TaskQueue} by enqueuing an unresolved {@link Task} to it. */
    public void enqueue() {
        synchronized (lock) {
            final TaskCompletionSource<Void> tcs = new TaskCompletionSource();
            taskQueue.enqueue(task -> tcs.getTask());
            pendingTasks.add(tcs);
        }
    }

    /** Resumes the {@link TaskQueue} by resolving the first {@link Task}. */
    public void dequeue() {
        synchronized (lock) {
            TaskCompletionSource<Void> tcs = pendingTasks.remove(0);
            tcs.setResult(null);
        }
    }
}

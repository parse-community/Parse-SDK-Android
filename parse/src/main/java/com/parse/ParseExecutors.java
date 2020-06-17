/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;

import com.parse.boltsinternal.Task;

class ParseExecutors {

    private static final Object SCHEDULED_EXECUTOR_LOCK = new Object();
    private static ScheduledExecutorService scheduledExecutor;

    /**
     * Long running operations should NOT be put onto SCHEDULED_EXECUTOR.
     */
    /* package */
    static ScheduledExecutorService scheduled() {
        synchronized (SCHEDULED_EXECUTOR_LOCK) {
            if (scheduledExecutor == null) {
                scheduledExecutor = java.util.concurrent.Executors.newScheduledThreadPool(1);
            }
        }
        return scheduledExecutor;
    }

    /* package */
    static Executor main() {
        return Task.UI_THREAD_EXECUTOR;
    }

    /* package */
    static Executor io() {
        return Task.BACKGROUND_EXECUTOR;
    }
}

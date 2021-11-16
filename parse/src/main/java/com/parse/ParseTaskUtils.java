/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import com.parse.boltsinternal.AggregateException;
import com.parse.boltsinternal.Continuation;
import com.parse.boltsinternal.Task;
import com.parse.boltsinternal.TaskCompletionSource;
import java.util.concurrent.CancellationException;

class ParseTaskUtils {

    /** Converts a task execution into a synchronous action. */
    // TODO (grantland): Task.cs actually throws an AggregateException if the task was cancelled
    // with
    // TaskCancellationException as an inner exception or an AggregateException with the original
    // exception as an inner exception if task.isFaulted().
    // https://msdn.microsoft.com/en-us/library/dd235635(v=vs.110).aspx
    /* package */
    static <T> T wait(Task<T> task) throws ParseException {
        try {
            task.waitForCompletion();
            if (task.isFaulted()) {
                Exception error = task.getError();
                if (error instanceof ParseException) {
                    throw (ParseException) error;
                }
                if (error instanceof AggregateException) {
                    throw new ParseException(error);
                }
                if (error instanceof RuntimeException) {
                    throw (RuntimeException) error;
                }
                throw new RuntimeException(error);
            } else if (task.isCancelled()) {
                throw new RuntimeException(new CancellationException());
            }
            return task.getResult();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    // region Task to Callbacks

    /**
     * Calls the callback after a task completes on the main thread, returning a Task that completes
     * with the same result as the input task after the callback has been run.
     */
    /* package */
    static Task<Void> callbackOnMainThreadAsync(
            Task<Void> task, final ParseCallback1<ParseException> callback) {
        return callbackOnMainThreadAsync(task, callback, false);
    }

    /**
     * Calls the callback after a task completes on the main thread, returning a Task that completes
     * with the same result as the input task after the callback has been run. If reportCancellation
     * is false, the callback will not be called if the task was cancelled.
     */
    /* package */
    static Task<Void> callbackOnMainThreadAsync(
            Task<Void> task,
            final ParseCallback1<ParseException> callback,
            final boolean reportCancellation) {
        if (callback == null) {
            return task;
        }
        return callbackOnMainThreadAsync(task, (aVoid, e) -> callback.done(e), reportCancellation);
    }

    /**
     * Calls the callback after a task completes on the main thread, returning a Task that completes
     * with the same result as the input task after the callback has been run.
     */
    /* package */
    static <T> Task<T> callbackOnMainThreadAsync(
            Task<T> task, final ParseCallback2<T, ParseException> callback) {
        return callbackOnMainThreadAsync(task, callback, false);
    }

    /**
     * Calls the callback after a task completes on the main thread, returning a Task that completes
     * with the same result as the input task after the callback has been run. If reportCancellation
     * is false, the callback will not be called if the task was cancelled.
     */
    /* package */
    static <T> Task<T> callbackOnMainThreadAsync(
            Task<T> task,
            final ParseCallback2<T, ParseException> callback,
            final boolean reportCancellation) {
        if (callback == null) {
            return task;
        }
        final TaskCompletionSource<T> tcs = new TaskCompletionSource<>();
        task.continueWith(
                (Continuation<T, Void>)
                        task1 -> {
                            if (task1.isCancelled() && !reportCancellation) {
                                tcs.setCancelled();
                                return null;
                            }
                            ParseExecutors.main()
                                    .execute(
                                            () -> {
                                                try {
                                                    Exception error = task1.getError();
                                                    if (error != null
                                                            && !(error instanceof ParseException)) {
                                                        error = new ParseException(error);
                                                    }
                                                    callback.done(
                                                            task1.getResult(),
                                                            (ParseException) error);
                                                } finally {
                                                    if (task1.isCancelled()) {
                                                        tcs.setCancelled();
                                                    } else if (task1.isFaulted()) {
                                                        tcs.setError(task1.getError());
                                                    } else {
                                                        tcs.setResult(task1.getResult());
                                                    }
                                                }
                                            });
                            return null;
                        });
        return tcs.getTask();
    }

    // endregion
}

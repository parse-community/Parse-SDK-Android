/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import java.util.concurrent.CancellationException;

import bolts.AggregateException;
import bolts.Continuation;
import bolts.Task;
import bolts.TaskCompletionSource;

class ParseTaskUtils {

    /**
     * Converts a task execution into a synchronous action.
     */
    //TODO (grantland): Task.cs actually throws an AggregateException if the task was cancelled with
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

    //region Task to Callbacks

    /**
     * Calls the callback after a task completes on the main thread, returning a Task that completes
     * with the same result as the input task after the callback has been run.
     */
    /* package */
    static Task<Void> callbackOnMainThreadAsync(Task<Void> task,
                                                final ParseCallback1<ParseException> callback) {
        return callbackOnMainThreadAsync(task, callback, false);
    }

    /**
     * Calls the callback after a task completes on the main thread, returning a Task that completes
     * with the same result as the input task after the callback has been run. If reportCancellation
     * is false, the callback will not be called if the task was cancelled.
     */
    /* package */
    static Task<Void> callbackOnMainThreadAsync(Task<Void> task,
                                                final ParseCallback1<ParseException> callback, final boolean reportCancellation) {
        if (callback == null) {
            return task;
        }
        return callbackOnMainThreadAsync(task, new ParseCallback2<Void, ParseException>() {
            @Override
            public void done(Void aVoid, ParseException e) {
                callback.done(e);
            }
        }, reportCancellation);
    }


    /**
     * Calls the callback after a task completes on the main thread, returning a Task that completes
     * with the same result as the input task after the callback has been run.
     */
    /* package */
    static <T> Task<T> callbackOnMainThreadAsync(Task<T> task,
                                                 final ParseCallback2<T, ParseException> callback) {
        return callbackOnMainThreadAsync(task, callback, false);
    }

    /**
     * Calls the callback after a task completes on the main thread, returning a Task that completes
     * with the same result as the input task after the callback has been run. If reportCancellation
     * is false, the callback will not be called if the task was cancelled.
     */
    /* package */
    static <T> Task<T> callbackOnMainThreadAsync(Task<T> task,
                                                 final ParseCallback2<T, ParseException> callback, final boolean reportCancellation) {
        if (callback == null) {
            return task;
        }
        final TaskCompletionSource<T> tcs = new TaskCompletionSource();
        task.continueWith(new Continuation<T, Void>() {
            @Override
            public Void then(final Task<T> task) {
                if (task.isCancelled() && !reportCancellation) {
                    tcs.setCancelled();
                    return null;
                }
                ParseExecutors.main().execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Exception error = task.getError();
                            if (error != null && !(error instanceof ParseException)) {
                                error = new ParseException(error);
                            }
                            callback.done(task.getResult(), (ParseException) error);
                        } finally {
                            if (task.isCancelled()) {
                                tcs.setCancelled();
                            } else if (task.isFaulted()) {
                                tcs.setError(task.getError());
                            } else {
                                tcs.setResult(task.getResult());
                            }
                        }
                    }
                });
                return null;
            }
        });
        return tcs.getTask();
    }

    //endregion
}

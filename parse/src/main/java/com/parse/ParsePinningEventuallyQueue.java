/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;


import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

import com.parse.boltsinternal.Continuation;
import com.parse.boltsinternal.Task;
import com.parse.boltsinternal.TaskCompletionSource;

/**
 * Manages all *Eventually calls when the local datastore is enabled.
 * <p>
 * Constraints:
 * - *Eventually calls must be executed in the same order they were queued.
 * - *Eventually calls must only be executed when it's ParseOperationSet is ready in
 * {@link ParseObject#taskQueue}.
 * - All rules apply on start from reboot.
 */
class ParsePinningEventuallyQueue extends ParseEventuallyQueue {
    private static final String TAG = "ParsePinningEventuallyQueue";
    private final Object connectionLock = new Object();
    private final ParseHttpClient httpClient;
    /**
     * Lock to make sure all changes to the below parameters happen atomically.
     */
    private final Object taskQueueSyncLock = new Object();
    /**
     * TCS that is held until a {@link ParseOperationSet} is completed.
     */
    private HashMap<String, TaskCompletionSource<JSONObject>> pendingOperationSetUUIDTasks =
            new HashMap<>();
    /**
     * Queue for reading/writing eventually operations. Makes all reads/writes atomic operations.
     */
    private TaskQueue taskQueue = new TaskQueue();
    /**
     * Queue for running *Eventually operations. It uses waitForOperationSetAndEventuallyPin to
     * synchronize {@link ParseObject#taskQueue} until they are both ready to process the same
     * ParseOperationSet.
     */
    private TaskQueue operationSetTaskQueue = new TaskQueue();
    /**
     * List of {@link ParseOperationSet#uuid} that are currently queued in
     * {@link ParsePinningEventuallyQueue#operationSetTaskQueue}.
     */
    private ArrayList<String> eventuallyPinUUIDQueue = new ArrayList<>();
    /**
     * TCS that is created when there is no internet connection and isn't resolved until connectivity
     * is achieved.
     * <p>
     * If an error is set, it means that we are trying to clear out the taskQueues.
     */
    private TaskCompletionSource<Void> connectionTaskCompletionSource = new TaskCompletionSource<>();
    private ConnectivityNotifier notifier;
    private ConnectivityNotifier.ConnectivityListener listener = new ConnectivityNotifier.ConnectivityListener() {
        @Override
        public void networkConnectivityStatusChanged(Context context, Intent intent) {
            boolean connectionLost =
                    intent.getBooleanExtra(ConnectivityManager.EXTRA_NO_CONNECTIVITY, false);
            if (connectionLost) {
                setConnected(false);
            } else {
                setConnected(ConnectivityNotifier.isConnected(context));
            }
        }
    };
    /**
     * Map of eventually operation UUID to TCS that is resolved when the operation is complete.
     */
    private HashMap<String, TaskCompletionSource<JSONObject>> pendingEventuallyTasks =
            new HashMap<>();
    /**
     * Map of eventually operation UUID to matching ParseOperationSet.
     */
    private HashMap<String, ParseOperationSet> uuidToOperationSet = new HashMap<>();
    /**
     * Map of eventually operation UUID to matching EventuallyPin.
     */
    private HashMap<String, EventuallyPin> uuidToEventuallyPin = new HashMap<>();

    public ParsePinningEventuallyQueue(Context context, ParseHttpClient client) {
        setConnected(ConnectivityNotifier.isConnected(context));

        httpClient = client;

        notifier = ConnectivityNotifier.getNotifier(context);
        notifier.addListener(listener);

        resume();
    }

    @Override
    public void onDestroy() {
        //TODO (grantland): pause #6484855

        notifier.removeListener(listener);
    }

    @Override
    public void setConnected(boolean connected) {
        synchronized (connectionLock) {
            if (isConnected() != connected) {
                super.setConnected(connected);
                if (connected) {
                    connectionTaskCompletionSource.trySetResult(null);
                    connectionTaskCompletionSource = new TaskCompletionSource<>();
                    connectionTaskCompletionSource.trySetResult(null);
                } else {
                    connectionTaskCompletionSource = new TaskCompletionSource<>();
                }
            }
        }
    }

    @Override
    public int pendingCount() {
        try {
            return ParseTaskUtils.wait(pendingCountAsync());
        } catch (ParseException e) {
            throw new IllegalStateException(e);
        }
    }

    public Task<Integer> pendingCountAsync() {
        final TaskCompletionSource<Integer> tcs = new TaskCompletionSource<>();

        taskQueue.enqueue(new Continuation<Void, Task<Void>>() {
            @Override
            public Task<Void> then(Task<Void> toAwait) {
                return pendingCountAsync(toAwait).continueWithTask(new Continuation<Integer, Task<Void>>() {
                    @Override
                    public Task<Void> then(Task<Integer> task) {
                        int count = task.getResult();
                        tcs.setResult(count);
                        return Task.forResult(null);
                    }
                });
            }
        });

        return tcs.getTask();
    }

    public Task<Integer> pendingCountAsync(Task<Void> toAwait) {
        return toAwait.continueWithTask(new Continuation<Void, Task<Integer>>() {
            @Override
            public Task<Integer> then(Task<Void> task) {
                return EventuallyPin.findAllPinned().continueWithTask(new Continuation<List<EventuallyPin>, Task<Integer>>() {
                    @Override
                    public Task<Integer> then(Task<List<EventuallyPin>> task) {
                        List<EventuallyPin> pins = task.getResult();
                        return Task.forResult(pins.size());
                    }
                });
            }
        });
    }

    @Override
    public void pause() {
        synchronized (connectionLock) {
            // Error out tasks waiting on waitForConnectionAsync.
            connectionTaskCompletionSource.trySetError(new PauseException());
            connectionTaskCompletionSource = new TaskCompletionSource<>();
            connectionTaskCompletionSource.trySetError(new PauseException());
        }

        synchronized (taskQueueSyncLock) {
            for (String key : pendingEventuallyTasks.keySet()) {
                // Error out tasks waiting on waitForOperationSetAndEventuallyPin.
                pendingEventuallyTasks.get(key).trySetError(new PauseException());
            }
            pendingEventuallyTasks.clear();
            uuidToOperationSet.clear();
            uuidToEventuallyPin.clear();
        }

        try {
            ParseTaskUtils.wait(whenAll(Arrays.asList(taskQueue, operationSetTaskQueue)));
        } catch (ParseException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void resume() {
        // Reset waitForConnectionAsync.
        if (isConnected()) {
            connectionTaskCompletionSource.trySetResult(null);
            connectionTaskCompletionSource = new TaskCompletionSource<>();
            connectionTaskCompletionSource.trySetResult(null);
        } else {
            connectionTaskCompletionSource = new TaskCompletionSource<>();
        }

        populateQueueAsync();
    }

    private Task<Void> waitForConnectionAsync() {
        synchronized (connectionLock) {
            return connectionTaskCompletionSource.getTask();
        }
    }

    /**
     * Pins the eventually operation on {@link ParsePinningEventuallyQueue#taskQueue}.
     *
     * @return Returns a Task that will be resolved when the command completes.
     */
    @Override
    public Task<JSONObject> enqueueEventuallyAsync(final ParseRESTCommand command,
                                                   final ParseObject object) {
        Parse.requirePermission(Manifest.permission.ACCESS_NETWORK_STATE);
        final TaskCompletionSource<JSONObject> tcs = new TaskCompletionSource<>();

        taskQueue.enqueue(new Continuation<Void, Task<Void>>() {
            @Override
            public Task<Void> then(Task<Void> toAwait) {
                return enqueueEventuallyAsync(command, object, toAwait, tcs);
            }
        });

        return tcs.getTask();
    }

    private Task<Void> enqueueEventuallyAsync(final ParseRESTCommand command,
                                              final ParseObject object, Task<Void> toAwait, final TaskCompletionSource<JSONObject> tcs) {
        return toAwait.continueWithTask(new Continuation<Void, Task<Void>>() {
            @Override
            public Task<Void> then(Task<Void> toAwait) {
                Task<EventuallyPin> pinTask = EventuallyPin.pinEventuallyCommand(object, command);

                return pinTask.continueWithTask(new Continuation<EventuallyPin, Task<Void>>() {
                    @Override
                    public Task<Void> then(Task<EventuallyPin> task) {
                        EventuallyPin pin = task.getResult();
                        Exception error = task.getError();
                        if (error != null) {
                            if (Parse.LOG_LEVEL_WARNING >= Parse.getLogLevel()) {
                                PLog.w(TAG, "Unable to save command for later.", error);
                            }
                            notifyTestHelper(TestHelper.COMMAND_NOT_ENQUEUED);
                            return Task.forResult(null);
                        }

                        pendingOperationSetUUIDTasks.put(pin.getUUID(), tcs);

                        // We don't need to wait for this.
                        populateQueueAsync().continueWithTask(new Continuation<Void, Task<Void>>() {
                            @Override
                            public Task<Void> then(Task<Void> task) {
                                /*
                                 * We need to wait until after we populated the operationSetTaskQueue to notify
                                 * that we've enqueued this command.
                                 */
                                notifyTestHelper(TestHelper.COMMAND_ENQUEUED);
                                return task;
                            }
                        });

                        return task.makeVoid();
                    }
                });
            }
        });
    }

    /**
     * Queries for pinned eventually operations on {@link ParsePinningEventuallyQueue#taskQueue}.
     *
     * @return Returns a Task that is resolved when all EventuallyPins are enqueued in the
     * operationSetTaskQueue.
     */
    private Task<Void> populateQueueAsync() {
        return taskQueue.enqueue(new Continuation<Void, Task<Void>>() {
            @Override
            public Task<Void> then(Task<Void> toAwait) {
                return populateQueueAsync(toAwait);
            }
        });
    }

    private Task<Void> populateQueueAsync(Task<Void> toAwait) {
        return toAwait.continueWithTask(new Continuation<Void, Task<List<EventuallyPin>>>() {
            @Override
            public Task<List<EventuallyPin>> then(Task<Void> task) {
                // We don't want to enqueue any EventuallyPins that are already queued.
                return EventuallyPin.findAllPinned(eventuallyPinUUIDQueue);
            }
        }).onSuccessTask(new Continuation<List<EventuallyPin>, Task<Void>>() {
            @Override
            public Task<Void> then(Task<List<EventuallyPin>> task) {
                List<EventuallyPin> pins = task.getResult();

                for (final EventuallyPin pin : pins) {
                    // We don't need to wait for this.
                    runEventuallyAsync(pin);
                }

                return task.makeVoid();
            }
        });
    }

    /**
     * Queues an eventually operation on {@link ParsePinningEventuallyQueue#operationSetTaskQueue}.
     * <p>
     * Each eventually operation is run synchronously to maintain the order in which they were
     * enqueued.
     */
    private Task<Void> runEventuallyAsync(final EventuallyPin eventuallyPin) {
        final String uuid = eventuallyPin.getUUID();
        if (eventuallyPinUUIDQueue.contains(uuid)) {
            // We don't want to enqueue the same operation more than once.
            return Task.forResult(null);
        }
        eventuallyPinUUIDQueue.add(uuid);

        operationSetTaskQueue.enqueue(new Continuation<Void, Task<Void>>() {
            @Override
            public Task<Void> then(final Task<Void> toAwait) {
                return runEventuallyAsync(eventuallyPin, toAwait).continueWithTask(new Continuation<Void, Task<Void>>() {
                    @Override
                    public Task<Void> then(Task<Void> task) {
                        eventuallyPinUUIDQueue.remove(uuid);
                        return task;
                    }
                });
            }
        });

        return Task.forResult(null);
    }

    /**
     * Runs the eventually operation. It first waits for a valid connection and if it's a save, it
     * also waits for the ParseObject to be ready.
     *
     * @return A task that is resolved when the eventually operation completes.
     */
    private Task<Void> runEventuallyAsync(final EventuallyPin eventuallyPin, final Task<Void> toAwait) {
        return toAwait.continueWithTask(new Continuation<Void, Task<Void>>() {
            @Override
            public Task<Void> then(Task<Void> task) {
                return waitForConnectionAsync();
            }
        }).onSuccessTask(new Continuation<Void, Task<Void>>() {
            @Override
            public Task<Void> then(Task<Void> task) {
                return waitForOperationSetAndEventuallyPin(null, eventuallyPin).continueWithTask(new Continuation<JSONObject, Task<Void>>() {
                    @Override
                    public Task<Void> then(Task<JSONObject> task) {
                        Exception error = task.getError();
                        if (error != null) {
                            if (error instanceof PauseException) {
                                // Bubble up the PauseException.
                                return task.makeVoid();
                            }

                            if (Parse.LOG_LEVEL_ERROR >= Parse.getLogLevel()) {
                                PLog.e(TAG, "Failed to run command.", error);
                            }

                            notifyTestHelper(TestHelper.COMMAND_FAILED, error);
                        } else {
                            notifyTestHelper(TestHelper.COMMAND_SUCCESSFUL);
                        }

                        TaskCompletionSource<JSONObject> tcs =
                                pendingOperationSetUUIDTasks.remove(eventuallyPin.getUUID());
                        if (tcs != null) {
                            if (error != null) {
                                tcs.setError(error);
                            } else {
                                tcs.setResult(task.getResult());
                            }
                        }
                        return task.makeVoid();
                    }
                });
            }
        });
    }

    /**
     * Synchronizes ParseObject#taskQueue (Many) and ParseCommandCache#taskQueue (One). Each queue
     * will be held until both are ready, matched on operationSetUUID. Once both are ready, the
     * eventually task will be run.
     *
     * @param operationSet  From {@link ParseObject}
     * @param eventuallyPin From {@link ParsePinningEventuallyQueue}
     */
    //TODO (grantland): We can probably generalize this to synchronize/join more than 2 taskQueues
    @Override
    /* package */ Task<JSONObject> waitForOperationSetAndEventuallyPin(ParseOperationSet operationSet,
                                                                       EventuallyPin eventuallyPin) {
        if (eventuallyPin != null && eventuallyPin.getType() != EventuallyPin.TYPE_SAVE) {
            return process(eventuallyPin, null);
        }

        final String uuid; // The key we use to join the taskQueues
        final TaskCompletionSource<JSONObject> tcs;

        synchronized (taskQueueSyncLock) {
            if (operationSet != null && eventuallyPin == null) {
                uuid = operationSet.getUUID();
                uuidToOperationSet.put(uuid, operationSet);
            } else if (operationSet == null && eventuallyPin != null) {
                uuid = eventuallyPin.getOperationSetUUID();
                uuidToEventuallyPin.put(uuid, eventuallyPin);
            } else {
                throw new IllegalStateException("Either operationSet or eventuallyPin must be set.");
            }

            eventuallyPin = uuidToEventuallyPin.get(uuid);
            operationSet = uuidToOperationSet.get(uuid);

            if (eventuallyPin == null || operationSet == null) {
                if (pendingEventuallyTasks.containsKey(uuid)) {
                    tcs = pendingEventuallyTasks.get(uuid);
                } else {
                    tcs = new TaskCompletionSource<>();
                    pendingEventuallyTasks.put(uuid, tcs);
                }
                return tcs.getTask();
            } else {
                tcs = pendingEventuallyTasks.get(uuid);
            }
        }

        return process(eventuallyPin, operationSet).continueWithTask(new Continuation<JSONObject, Task<JSONObject>>() {
            @Override
            public Task<JSONObject> then(Task<JSONObject> task) {
                synchronized (taskQueueSyncLock) {
                    pendingEventuallyTasks.remove(uuid);
                    uuidToOperationSet.remove(uuid);
                    uuidToEventuallyPin.remove(uuid);
                }

                Exception error = task.getError();
                if (error != null) {
                    tcs.trySetError(error);
                } else if (task.isCancelled()) {
                    tcs.trySetCancelled();
                } else {
                    tcs.trySetResult(task.getResult());
                }
                return tcs.getTask();
            }
        });
    }

    /**
     * Invokes the eventually operation.
     */
    private Task<JSONObject> process(final EventuallyPin eventuallyPin,
                                     final ParseOperationSet operationSet) {

        return waitForConnectionAsync().onSuccessTask(new Continuation<Void, Task<JSONObject>>() {
            @Override
            public Task<JSONObject> then(Task<Void> task) throws Exception {
                final int type = eventuallyPin.getType();
                final ParseObject object = eventuallyPin.getObject();
                String sessionToken = eventuallyPin.getSessionToken();

                Task<JSONObject> executeTask;
                if (type == EventuallyPin.TYPE_SAVE) {
                    executeTask = object.saveAsync(httpClient, operationSet, sessionToken);
                } else if (type == EventuallyPin.TYPE_DELETE) {
                    executeTask = object.deleteAsync(sessionToken).cast();
                } else { // else if (type == EventuallyPin.TYPE_COMMAND) {
                    ParseRESTCommand command = eventuallyPin.getCommand();
                    if (command == null) {
                        executeTask = Task.forResult(null);
                        notifyTestHelper(TestHelper.COMMAND_OLD_FORMAT_DISCARDED);
                    } else {
                        executeTask = command.executeAsync(httpClient);
                    }
                }

                return executeTask.continueWithTask(new Continuation<JSONObject, Task<JSONObject>>() {
                    @Override
                    public Task<JSONObject> then(final Task<JSONObject> executeTask) {
                        Exception error = executeTask.getError();
                        if (error != null) {
                            if (error instanceof ParseException
                                    && ((ParseException) error).getCode() == ParseException.CONNECTION_FAILED) {
                                // We did our retry logic in ParseRequest, so just mark as not connected
                                // and move on.
                                setConnected(false);

                                notifyTestHelper(TestHelper.NETWORK_DOWN);

                                return process(eventuallyPin, operationSet);
                            }
                        }

                        // Delete the command regardless, even if it failed. Otherwise, we'll just keep
                        // trying it forever.
                        // We don't have to wait for taskQueue since it will not be enqueued again
                        // since this EventuallyPin is still in eventuallyPinUUIDQueue.
                        return eventuallyPin.unpinInBackground(EventuallyPin.PIN_NAME).continueWithTask(new Continuation<Void, Task<Void>>() {
                            @Override
                            public Task<Void> then(Task<Void> task) {
                                JSONObject result = executeTask.getResult();
                                if (type == EventuallyPin.TYPE_SAVE) {
                                    return object.handleSaveEventuallyResultAsync(result, operationSet);
                                } else if (type == EventuallyPin.TYPE_DELETE) {
                                    if (executeTask.isFaulted()) {
                                        return task;
                                    } else {
                                        return object.handleDeleteEventuallyResultAsync();
                                    }
                                } else { // else if (type == EventuallyPin.TYPE_COMMAND) {
                                    return task;
                                }
                            }
                        }).continueWithTask(new Continuation<Void, Task<JSONObject>>() {
                            @Override
                            public Task<JSONObject> then(Task<Void> task) {
                                return executeTask;
                            }
                        });
                    }
                });
            }
        });
    }

    @Override
        /* package */ void simulateReboot() {
        pause();

        pendingOperationSetUUIDTasks.clear();
        pendingEventuallyTasks.clear();
        uuidToOperationSet.clear();
        uuidToEventuallyPin.clear();

        resume();
    }

    @Override
    public void clear() {
        pause();

        Task<Void> task = taskQueue.enqueue(new Continuation<Void, Task<Void>>() {
            @Override
            public Task<Void> then(Task<Void> toAwait) {
                return toAwait.continueWithTask(new Continuation<Void, Task<Void>>() {
                    @Override
                    public Task<Void> then(Task<Void> task) {
                        return EventuallyPin.findAllPinned().onSuccessTask(new Continuation<List<EventuallyPin>, Task<Void>>() {
                            @Override
                            public Task<Void> then(Task<List<EventuallyPin>> task) {
                                List<EventuallyPin> pins = task.getResult();

                                List<Task<Void>> tasks = new ArrayList<>();
                                for (EventuallyPin pin : pins) {
                                    tasks.add(pin.unpinInBackground(EventuallyPin.PIN_NAME));
                                }
                                return Task.whenAll(tasks);
                            }
                        });
                    }
                });
            }
        });

        try {
            ParseTaskUtils.wait(task);
        } catch (ParseException e) {
            throw new IllegalStateException(e);
        }

        simulateReboot();

        resume();
    }

    /**
     * Creates a Task that is resolved when all the TaskQueues are "complete".
     * <p>
     * "Complete" is when all the TaskQueues complete the queue of Tasks that were in it before
     * whenAll was invoked. This will not keep track of tasks that are added on after whenAll
     * was invoked.
     */
    private Task<Void> whenAll(Collection<TaskQueue> taskQueues) {
        List<Task<Void>> tasks = new ArrayList<>();

        for (TaskQueue taskQueue : taskQueues) {
            Task<Void> task = taskQueue.enqueue(new Continuation<Void, Task<Void>>() {
                @Override
                public Task<Void> then(Task<Void> toAwait) {
                    return toAwait;
                }
            });

            tasks.add(task);
        }

        return Task.whenAll(tasks);
    }

    private static class PauseException extends Exception {
        // This class was intentionally left blank.
    }
}

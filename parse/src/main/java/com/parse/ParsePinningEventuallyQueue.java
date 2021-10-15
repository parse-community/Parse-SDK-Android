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
import android.net.ConnectivityManager;
import com.parse.boltsinternal.Continuation;
import com.parse.boltsinternal.Task;
import com.parse.boltsinternal.TaskCompletionSource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import org.json.JSONObject;

/**
 * Manages all *Eventually calls when the local datastore is enabled.
 *
 * <p>Constraints: - *Eventually calls must be executed in the same order they were queued. -
 * *Eventually calls must only be executed when it's ParseOperationSet is ready in {@link
 * ParseObject#taskQueue}. - All rules apply on start from reboot.
 */
class ParsePinningEventuallyQueue extends ParseEventuallyQueue {
    private static final String TAG = "ParsePinningEventuallyQueue";
    private final Object connectionLock = new Object();
    private final ParseHttpClient httpClient;
    /** Lock to make sure all changes to the below parameters happen atomically. */
    private final Object taskQueueSyncLock = new Object();
    /** TCS that is held until a {@link ParseOperationSet} is completed. */
    private final HashMap<String, TaskCompletionSource<JSONObject>> pendingOperationSetUUIDTasks =
            new HashMap<>();
    /**
     * Queue for reading/writing eventually operations. Makes all reads/writes atomic operations.
     */
    private final TaskQueue taskQueue = new TaskQueue();
    /**
     * Queue for running *Eventually operations. It uses waitForOperationSetAndEventuallyPin to
     * synchronize {@link ParseObject#taskQueue} until they are both ready to process the same
     * ParseOperationSet.
     */
    private final TaskQueue operationSetTaskQueue = new TaskQueue();
    /**
     * List of {@link ParseOperationSet#uuid} that are currently queued in {@link
     * ParsePinningEventuallyQueue#operationSetTaskQueue}.
     */
    private final ArrayList<String> eventuallyPinUUIDQueue = new ArrayList<>();

    private final ConnectivityNotifier notifier;
    /** Map of eventually operation UUID to TCS that is resolved when the operation is complete. */
    private final HashMap<String, TaskCompletionSource<JSONObject>> pendingEventuallyTasks =
            new HashMap<>();
    /** Map of eventually operation UUID to matching ParseOperationSet. */
    private final HashMap<String, ParseOperationSet> uuidToOperationSet = new HashMap<>();
    /** Map of eventually operation UUID to matching EventuallyPin. */
    private final HashMap<String, EventuallyPin> uuidToEventuallyPin = new HashMap<>();
    /**
     * TCS that is created when there is no internet connection and isn't resolved until
     * connectivity is achieved.
     *
     * <p>If an error is set, it means that we are trying to clear out the taskQueues.
     */
    private TaskCompletionSource<Void> connectionTaskCompletionSource =
            new TaskCompletionSource<>();

    private final ConnectivityNotifier.ConnectivityListener listener =
            (context, intent) -> {
                boolean connectionLost =
                        intent.getBooleanExtra(ConnectivityManager.EXTRA_NO_CONNECTIVITY, false);
                if (connectionLost) {
                    setConnected(false);
                } else {
                    setConnected(ConnectivityNotifier.isConnected(context));
                }
            };

    public ParsePinningEventuallyQueue(Context context, ParseHttpClient client) {
        setConnected(ConnectivityNotifier.isConnected(context));

        httpClient = client;

        notifier = ConnectivityNotifier.getNotifier(context);
        notifier.addListener(listener);

        resume();
    }

    @Override
    public void onDestroy() {
        // TODO (grantland): pause #6484855

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

        taskQueue.enqueue(
                (Continuation<Void, Task<Void>>)
                        toAwait ->
                                pendingCountAsync(toAwait)
                                        .continueWithTask(
                                                task -> {
                                                    int count = task.getResult();
                                                    tcs.setResult(count);
                                                    return Task.forResult(null);
                                                }));

        return tcs.getTask();
    }

    public Task<Integer> pendingCountAsync(Task<Void> toAwait) {
        return toAwait.continueWithTask(
                task ->
                        EventuallyPin.findAllPinned()
                                .continueWithTask(
                                        task1 -> {
                                            List<EventuallyPin> pins = task1.getResult();
                                            return Task.forResult(pins.size());
                                        }));
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
    public Task<JSONObject> enqueueEventuallyAsync(
            final ParseRESTCommand command, final ParseObject object) {
        Parse.requirePermission(Manifest.permission.ACCESS_NETWORK_STATE);
        final TaskCompletionSource<JSONObject> tcs = new TaskCompletionSource<>();

        taskQueue.enqueue(toAwait -> enqueueEventuallyAsync(command, object, toAwait, tcs));

        return tcs.getTask();
    }

    private Task<Void> enqueueEventuallyAsync(
            final ParseRESTCommand command,
            final ParseObject object,
            Task<Void> toAwait,
            final TaskCompletionSource<JSONObject> tcs) {
        return toAwait.continueWithTask(
                toAwait1 -> {
                    Task<EventuallyPin> pinTask =
                            EventuallyPin.pinEventuallyCommand(object, command);

                    return pinTask.continueWithTask(
                            task -> {
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
                                populateQueueAsync()
                                        .continueWithTask(
                                                task1 -> {
                                                    /*
                                                     * We need to wait until after we populated the operationSetTaskQueue to notify
                                                     * that we've enqueued this command.
                                                     */
                                                    notifyTestHelper(TestHelper.COMMAND_ENQUEUED);
                                                    return task1;
                                                });

                                return task.makeVoid();
                            });
                });
    }

    /**
     * Queries for pinned eventually operations on {@link ParsePinningEventuallyQueue#taskQueue}.
     *
     * @return Returns a Task that is resolved when all EventuallyPins are enqueued in the
     *     operationSetTaskQueue.
     */
    private Task<Void> populateQueueAsync() {
        return taskQueue.enqueue(this::populateQueueAsync);
    }

    private Task<Void> populateQueueAsync(Task<Void> toAwait) {
        return toAwait.continueWithTask(
                        task -> {
                            // We don't want to enqueue any EventuallyPins that are already queued.
                            return EventuallyPin.findAllPinned(eventuallyPinUUIDQueue);
                        })
                .onSuccessTask(
                        task -> {
                            List<EventuallyPin> pins = task.getResult();

                            for (final EventuallyPin pin : pins) {
                                // We don't need to wait for this.
                                runEventuallyAsync(pin);
                            }

                            return task.makeVoid();
                        });
    }

    /**
     * Queues an eventually operation on {@link ParsePinningEventuallyQueue#operationSetTaskQueue}.
     *
     * <p>Each eventually operation is run synchronously to maintain the order in which they were
     * enqueued.
     */
    private Task<Void> runEventuallyAsync(final EventuallyPin eventuallyPin) {
        final String uuid = eventuallyPin.getUUID();
        if (eventuallyPinUUIDQueue.contains(uuid)) {
            // We don't want to enqueue the same operation more than once.
            return Task.forResult(null);
        }
        eventuallyPinUUIDQueue.add(uuid);

        operationSetTaskQueue.enqueue(
                toAwait ->
                        runEventuallyAsync(eventuallyPin, toAwait)
                                .continueWithTask(
                                        task -> {
                                            eventuallyPinUUIDQueue.remove(uuid);
                                            return task;
                                        }));

        return Task.forResult(null);
    }

    /**
     * Runs the eventually operation. It first waits for a valid connection and if it's a save, it
     * also waits for the ParseObject to be ready.
     *
     * @return A task that is resolved when the eventually operation completes.
     */
    private Task<Void> runEventuallyAsync(
            final EventuallyPin eventuallyPin, final Task<Void> toAwait) {
        return toAwait.continueWithTask(task -> waitForConnectionAsync())
                .onSuccessTask(
                        task ->
                                waitForOperationSetAndEventuallyPin(null, eventuallyPin)
                                        .continueWithTask(
                                                task1 -> {
                                                    Exception error = task1.getError();
                                                    if (error != null) {
                                                        if (error instanceof PauseException) {
                                                            // Bubble up the PauseException.
                                                            return task1.makeVoid();
                                                        }

                                                        if (Parse.LOG_LEVEL_ERROR
                                                                >= Parse.getLogLevel()) {
                                                            PLog.e(
                                                                    TAG,
                                                                    "Failed to run command.",
                                                                    error);
                                                        }

                                                        notifyTestHelper(
                                                                TestHelper.COMMAND_FAILED, error);
                                                    } else {
                                                        notifyTestHelper(
                                                                TestHelper.COMMAND_SUCCESSFUL);
                                                    }

                                                    TaskCompletionSource<JSONObject> tcs =
                                                            pendingOperationSetUUIDTasks.remove(
                                                                    eventuallyPin.getUUID());
                                                    if (tcs != null) {
                                                        if (error != null) {
                                                            tcs.setError(error);
                                                        } else {
                                                            tcs.setResult(task1.getResult());
                                                        }
                                                    }
                                                    return task1.makeVoid();
                                                }));
    }

    /**
     * Synchronizes ParseObject#taskQueue (Many) and ParseCommandCache#taskQueue (One). Each queue
     * will be held until both are ready, matched on operationSetUUID. Once both are ready, the
     * eventually task will be run.
     *
     * @param operationSet From {@link ParseObject}
     * @param eventuallyPin From {@link ParsePinningEventuallyQueue}
     */
    // TODO (grantland): We can probably generalize this to synchronize/join more than 2 taskQueues
    @Override
    /* package */ Task<JSONObject> waitForOperationSetAndEventuallyPin(
            ParseOperationSet operationSet, EventuallyPin eventuallyPin) {
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
                throw new IllegalStateException(
                        "Either operationSet or eventuallyPin must be set.");
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

        return process(eventuallyPin, operationSet)
                .continueWithTask(
                        task -> {
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
                        });
    }

    /** Invokes the eventually operation. */
    private Task<JSONObject> process(
            final EventuallyPin eventuallyPin, final ParseOperationSet operationSet) {

        return waitForConnectionAsync()
                .onSuccessTask(
                        task -> {
                            final int type = eventuallyPin.getType();
                            final ParseObject object = eventuallyPin.getObject();
                            String sessionToken = eventuallyPin.getSessionToken();

                            Task<JSONObject> executeTask;
                            if (type == EventuallyPin.TYPE_SAVE) {
                                executeTask =
                                        object.saveAsync(httpClient, operationSet, sessionToken);
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

                            return executeTask.continueWithTask(
                                    executeTask1 -> {
                                        Exception error = executeTask1.getError();
                                        if (error != null) {
                                            if (error instanceof ParseException
                                                    && ((ParseException) error).getCode()
                                                            == ParseException.CONNECTION_FAILED) {
                                                // We did our retry logic in ParseRequest, so just
                                                // mark as not connected
                                                // and move on.
                                                setConnected(false);

                                                notifyTestHelper(TestHelper.NETWORK_DOWN);

                                                return process(eventuallyPin, operationSet);
                                            }
                                        }

                                        // Delete the command regardless, even if it failed.
                                        // Otherwise, we'll just keep
                                        // trying it forever.
                                        // We don't have to wait for taskQueue since it will not be
                                        // enqueued again
                                        // since this EventuallyPin is still in
                                        // eventuallyPinUUIDQueue.
                                        return eventuallyPin
                                                .unpinInBackground(EventuallyPin.PIN_NAME)
                                                .continueWithTask(
                                                        task12 -> {
                                                            JSONObject result =
                                                                    executeTask1.getResult();
                                                            if (type == EventuallyPin.TYPE_SAVE) {
                                                                return object
                                                                        .handleSaveEventuallyResultAsync(
                                                                                result,
                                                                                operationSet);
                                                            } else if (type
                                                                    == EventuallyPin.TYPE_DELETE) {
                                                                if (executeTask1.isFaulted()) {
                                                                    return task12;
                                                                } else {
                                                                    return object
                                                                            .handleDeleteEventuallyResultAsync();
                                                                }
                                                            } else { // else if (type ==
                                                                // EventuallyPin.TYPE_COMMAND)
                                                                // {
                                                                return task12;
                                                            }
                                                        })
                                                .continueWithTask(task1 -> executeTask1);
                                    });
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

        Task<Void> task =
                taskQueue.enqueue(
                        toAwait ->
                                toAwait.continueWithTask(
                                        task12 ->
                                                EventuallyPin.findAllPinned()
                                                        .onSuccessTask(
                                                                task1 -> {
                                                                    List<EventuallyPin> pins =
                                                                            task1.getResult();

                                                                    List<Task<Void>> tasks =
                                                                            new ArrayList<>();
                                                                    for (EventuallyPin pin : pins) {
                                                                        tasks.add(
                                                                                pin
                                                                                        .unpinInBackground(
                                                                                                EventuallyPin
                                                                                                        .PIN_NAME));
                                                                    }
                                                                    return Task.whenAll(tasks);
                                                                })));

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
     *
     * <p>"Complete" is when all the TaskQueues complete the queue of Tasks that were in it before
     * whenAll was invoked. This will not keep track of tasks that are added on after whenAll was
     * invoked.
     */
    private Task<Void> whenAll(Collection<TaskQueue> taskQueues) {
        List<Task<Void>> tasks = new ArrayList<>();

        for (TaskQueue taskQueue : taskQueues) {
            Task<Void> task = taskQueue.enqueue(toAwait -> toAwait);

            tasks.add(task);
        }

        return Task.whenAll(tasks);
    }

    private static class PauseException extends Exception {
        // This class was intentionally left blank.
    }
}

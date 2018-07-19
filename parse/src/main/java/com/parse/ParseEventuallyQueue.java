/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import android.util.SparseArray;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import bolts.Task;

/* package */ abstract class ParseEventuallyQueue {

    private boolean isConnected;

    /**
     * Gets notifications of various events happening in the command cache, so that tests can be
     * synchronized.
     */
    private TestHelper testHelper;

    public abstract void onDestroy();

    public boolean isConnected() {
        return isConnected;
    }

    public void setConnected(boolean connected) {
        isConnected = connected;
    }

    public abstract int pendingCount();

    public void setTimeoutRetryWaitSeconds(double seconds) {
        // do nothing
    }

    public void setMaxCacheSizeBytes(int bytes) {
        // do nothing
    }

    /**
     * See class TestHelper below.
     */
    public TestHelper getTestHelper() {
        if (testHelper == null) {
            testHelper = new TestHelper();
        }
        return testHelper;
    }

    protected void notifyTestHelper(int event) {
        notifyTestHelper(event, null);
    }

    protected void notifyTestHelper(int event, Throwable t) {
        if (testHelper != null) {
            testHelper.notify(event, t);
        }
    }

    public abstract void pause();

    public abstract void resume();

    /**
     * Attempts to run the given command and any pending commands. Adds the command to the pending set
     * if it can't be run yet.
     *
     * @param command - The command to run.
     * @param object  - If this command references an unsaved object, we need to remove any previous command
     *                referencing that unsaved object. Otherwise, it will get created over and over again.
     *                So object is a reference to the object, if it has no objectId. Otherwise, it can be
     *                null.
     */
    public abstract Task<JSONObject> enqueueEventuallyAsync(ParseRESTCommand command,
                                                            ParseObject object);

    protected ParseRESTCommand commandFromJSON(JSONObject json)
            throws JSONException {
        ParseRESTCommand command = null;
        if (ParseRESTCommand.isValidCommandJSONObject(json)) {
            command = ParseRESTCommand.fromJSONObject(json);
        } else if (ParseRESTCommand.isValidOldFormatCommandJSONObject(json)) {
            // do nothing
        } else {
            throw new JSONException("Failed to load command from JSON.");
        }
        return command;
    }

    /* package */ Task<JSONObject> waitForOperationSetAndEventuallyPin(ParseOperationSet operationSet,
                                                                       EventuallyPin eventuallyPin) {
        return Task.forResult(null);
    }

    /* package */
    abstract void simulateReboot();

    /**
     * Gets rid of all pending commands.
     */
    public abstract void clear();

    /**
     * Fakes an object update notification for use in tests. This is used by saveEventually to make it
     * look like test code has updated an object through the command cache even if it actually
     * avoided executing update by determining the object wasn't dirty.
     */
    void fakeObjectUpdate() {
        if (testHelper != null) {
            testHelper.notify(TestHelper.COMMAND_ENQUEUED);
            testHelper.notify(TestHelper.COMMAND_SUCCESSFUL);
            testHelper.notify(TestHelper.OBJECT_UPDATED);
        }
    }

    /**
     * Gets notifications of various events happening in the command cache, so that tests can be
     * synchronized. See ParseCommandCacheTest for examples of how to use this.
     */
    public static class TestHelper {
        public static final int COMMAND_SUCCESSFUL = 1;
        public static final int COMMAND_FAILED = 2;
        public static final int COMMAND_ENQUEUED = 3;
        public static final int COMMAND_NOT_ENQUEUED = 4;
        public static final int OBJECT_UPDATED = 5;
        public static final int OBJECT_REMOVED = 6;
        public static final int NETWORK_DOWN = 7;
        public static final int COMMAND_OLD_FORMAT_DISCARDED = 8;
        private static final int MAX_EVENTS = 1000;
        private SparseArray<Semaphore> events = new SparseArray<>();

        private TestHelper() {
            clear();
        }

        public static String getEventString(int event) {
            switch (event) {
                case COMMAND_SUCCESSFUL:
                    return "COMMAND_SUCCESSFUL";
                case COMMAND_FAILED:
                    return "COMMAND_FAILED";
                case COMMAND_ENQUEUED:
                    return "COMMAND_ENQUEUED";
                case COMMAND_NOT_ENQUEUED:
                    return "COMMAND_NOT_ENQUEUED";
                case OBJECT_UPDATED:
                    return "OBJECT_UPDATED";
                case OBJECT_REMOVED:
                    return "OBJECT_REMOVED";
                case NETWORK_DOWN:
                    return "NETWORK_DOWN";
                case COMMAND_OLD_FORMAT_DISCARDED:
                    return "COMMAND_OLD_FORMAT_DISCARDED";
                default:
                    throw new IllegalStateException("Encountered unknown event: " + event);
            }
        }

        public void clear() {
            events.clear();
            events.put(COMMAND_SUCCESSFUL, new Semaphore(MAX_EVENTS));
            events.put(COMMAND_FAILED, new Semaphore(MAX_EVENTS));
            events.put(COMMAND_ENQUEUED, new Semaphore(MAX_EVENTS));
            events.put(COMMAND_NOT_ENQUEUED, new Semaphore(MAX_EVENTS));
            events.put(OBJECT_UPDATED, new Semaphore(MAX_EVENTS));
            events.put(OBJECT_REMOVED, new Semaphore(MAX_EVENTS));
            events.put(NETWORK_DOWN, new Semaphore(MAX_EVENTS));
            events.put(COMMAND_OLD_FORMAT_DISCARDED, new Semaphore(MAX_EVENTS));
            for (int i = 0; i < events.size(); i++) {
                int event = events.keyAt(i);
                events.get(event).acquireUninterruptibly(MAX_EVENTS);
            }
        }

        public int unexpectedEvents() {
            int sum = 0;
            for (int i = 0; i < events.size(); i++) {
                int event = events.keyAt(i);
                sum += events.get(event).availablePermits();
            }
            return sum;
        }

        public List<String> getUnexpectedEvents() {
            List<String> unexpectedEvents = new ArrayList<>();
            for (int i = 0; i < events.size(); i++) {
                int event = events.keyAt(i);
                if (events.get(event).availablePermits() > 0) {
                    unexpectedEvents.add(getEventString(event));
                }
            }
            return unexpectedEvents;
        }

        public void notify(int event) {
            notify(event, null);
        }

        public void notify(int event, Throwable t) {
            events.get(event).release();
        }

        public boolean waitFor(int event) {
            return waitFor(event, 1);
        }

        public boolean waitFor(int event, int permits) {
            try {
                return events.get(event).tryAcquire(permits, 10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace();
                return false;
            }
        }
    }
}

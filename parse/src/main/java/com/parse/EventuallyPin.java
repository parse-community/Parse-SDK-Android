/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import com.parse.http.ParseHttpRequest;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import bolts.Continuation;
import bolts.Task;

/**
 * Properties
 * - time
 * Used for sort order when querying for all EventuallyPins
 * - type
 * TYPE_SAVE or TYPE_DELETE
 * - object
 * The object that the operation should notify when complete
 * - operationSetUUID
 * The operationSet to be completed
 * - sessionToken
 * The user that instantiated the operation
 */
@ParseClassName("_EventuallyPin")
class EventuallyPin extends ParseObject {

    public static final String PIN_NAME = "_eventuallyPin";

    public static final int TYPE_SAVE = 1;
    public static final int TYPE_DELETE = 2;
    public static final int TYPE_COMMAND = 3;

    public EventuallyPin() {
        super("_EventuallyPin");
    }

    public static Task<EventuallyPin> pinEventuallyCommand(ParseObject object,
                                                           ParseRESTCommand command) {
        int type = TYPE_COMMAND;
        JSONObject json = null;
        if (command.httpPath.startsWith("classes")) {
            if (command.method == ParseHttpRequest.Method.POST ||
                    command.method == ParseHttpRequest.Method.PUT) {
                type = TYPE_SAVE;
            } else if (command.method == ParseHttpRequest.Method.DELETE) {
                type = TYPE_DELETE;
            }
        } else {
            json = command.toJSONObject();
        }
        return pinEventuallyCommand(
                type,
                object,
                command.getOperationSetUUID(),
                command.getSessionToken(),
                json);
    }

    /**
     * @param type             Type of the command: TYPE_SAVE, TYPE_DELETE, TYPE_COMMAND
     * @param obj              (Optional) Object the operation is being executed on. Required for TYPE_SAVE and
     *                         TYPE_DELETE.
     * @param operationSetUUID (Optional) UUID of the ParseOperationSet that is paired with the ParseCommand.
     *                         Required for TYPE_SAVE and TYPE_DELETE.
     * @param sessionToken     (Optional) The sessionToken for the command. Required for TYPE_SAVE and TYPE_DELETE.
     * @param command          (Optional) JSON representation of the ParseCommand. Required for TYPE_COMMAND.
     * @return Returns a task that is resolved when the command is pinned.
     */
    private static Task<EventuallyPin> pinEventuallyCommand(int type, ParseObject obj,
                                                            String operationSetUUID, String sessionToken, JSONObject command) {
        final EventuallyPin pin = new EventuallyPin();
        pin.put("uuid", UUID.randomUUID().toString());
        pin.put("time", new Date());
        pin.put("type", type);
        if (obj != null) {
            pin.put("object", obj);
        }
        if (operationSetUUID != null) {
            pin.put("operationSetUUID", operationSetUUID);
        }
        if (sessionToken != null) {
            pin.put("sessionToken", sessionToken);
        }
        if (command != null) {
            pin.put("command", command);
        }
        return pin.pinInBackground(PIN_NAME).continueWith(new Continuation<Void, EventuallyPin>() {
            @Override
            public EventuallyPin then(Task<Void> task) {
                return pin;
            }
        });
    }

    public static Task<List<EventuallyPin>> findAllPinned() {
        return findAllPinned(null);
    }

    public static Task<List<EventuallyPin>> findAllPinned(Collection<String> excludeUUIDs) {
        ParseQuery<EventuallyPin> query = new ParseQuery<>(EventuallyPin.class)
                .fromPin(PIN_NAME)
                .ignoreACLs()
                .orderByAscending("time");

        if (excludeUUIDs != null) {
            query.whereNotContainedIn("uuid", excludeUUIDs);
        }

        // We need pass in a null user because we don't want the query to fetch the current user
        // from LDS.
        return query.findInBackground().onSuccessTask(new Continuation<List<EventuallyPin>, Task<List<EventuallyPin>>>() {
            @Override
            public Task<List<EventuallyPin>> then(Task<List<EventuallyPin>> task) {
                final List<EventuallyPin> pins = task.getResult();
                List<Task<Void>> tasks = new ArrayList<>();

                for (EventuallyPin pin : pins) {
                    ParseObject object = pin.getObject();
                    if (object != null) {
                        tasks.add(object.fetchFromLocalDatastoreAsync().makeVoid());
                    }
                }

                return Task.whenAll(tasks).continueWithTask(new Continuation<Void, Task<List<EventuallyPin>>>() {
                    @Override
                    public Task<List<EventuallyPin>> then(Task<Void> task) {
                        return Task.forResult(pins);
                    }
                });
            }
        });
    }

    @Override
    boolean needsDefaultACL() {
        return false;
    }

    public String getUUID() {
        return getString("uuid");
    }

    public int getType() {
        return getInt("type");
    }

    public ParseObject getObject() {
        return getParseObject("object");
    }

    public String getOperationSetUUID() {
        return getString("operationSetUUID");
    }

    public String getSessionToken() {
        return getString("sessionToken");
    }

    public ParseRESTCommand getCommand() throws JSONException {
        JSONObject json = getJSONObject("command");
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
}

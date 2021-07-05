/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse

import com.parse.ParseRESTCommand.Companion.fromJSONObject
import com.parse.ParseRESTCommand.Companion.isValidCommandJSONObject
import com.parse.ParseRESTCommand.Companion.isValidOldFormatCommandJSONObject
import com.parse.boltsinternal.Task
import com.parse.http.ParseHttpRequest
import org.json.JSONException
import org.json.JSONObject
import java.util.*

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
internal class EventuallyPin : ParseObject("_EventuallyPin") {
    public override fun needsDefaultACL(): Boolean {
        return false
    }

    val uUID: String?
        get() = getString("uuid")
    val type: Int
        get() = getInt("type")
    val `object`: ParseObject?
        get() = getParseObject("object")
    val operationSetUUID: String?
        get() = getString("operationSetUUID")
    val sessionToken: String?
        get() = getString("sessionToken")

    @get:Throws(JSONException::class)
    val command: ParseRESTCommand?
        get() {
            val json = getJSONObject("command")
            var command: ParseRESTCommand? = null
            if (isValidCommandJSONObject(json!!)) {
                command = fromJSONObject(json)
            } else if (!isValidOldFormatCommandJSONObject(json)) {
                throw JSONException("Failed to load command from JSON.")
            }
            return command
        }

    companion object {
        const val PIN_NAME = "_eventuallyPin"
        const val TYPE_SAVE = 1
        const val TYPE_DELETE = 2
        const val TYPE_COMMAND = 3

        fun pinEventuallyCommand(
            `object`: ParseObject?,
            command: ParseRESTCommand
        ): Task<EventuallyPin> {
            var type = TYPE_COMMAND
            var json: JSONObject? = null
            if (command.httpPath!!.startsWith("classes")) {
                if (command.method == ParseHttpRequest.Method.POST ||
                    command.method == ParseHttpRequest.Method.PUT
                ) {
                    type = TYPE_SAVE
                } else if (command.method == ParseHttpRequest.Method.DELETE) {
                    type = TYPE_DELETE
                }
            } else {
                json = command.toJSONObject()
            }
            return pinEventuallyCommand(
                type,
                `object`,
                command.operationSetUUID,
                command.sessionToken,
                json
            )
        }

        /**
         * @param type             Type of the command: TYPE_SAVE, TYPE_DELETE, TYPE_COMMAND
         * @param obj              (Optional) Object the operation is being executed on. Required for TYPE_SAVE and
         * TYPE_DELETE.
         * @param operationSetUUID (Optional) UUID of the ParseOperationSet that is paired with the ParseCommand.
         * Required for TYPE_SAVE and TYPE_DELETE.
         * @param sessionToken     (Optional) The sessionToken for the command. Required for TYPE_SAVE and TYPE_DELETE.
         * @param command          (Optional) JSON representation of the ParseCommand. Required for TYPE_COMMAND.
         * @return Returns a task that is resolved when the command is pinned.
         */
        private fun pinEventuallyCommand(
            type: Int, obj: ParseObject?,
            operationSetUUID: String?, sessionToken: String?, command: JSONObject?
        ): Task<EventuallyPin> {
            val pin = EventuallyPin()
            pin.put("uuid", UUID.randomUUID().toString())
            pin.put("time", Date())
            pin.put("type", type)
            if (obj != null) {
                pin.put("object", obj)
            }
            if (operationSetUUID != null) {
                pin.put("operationSetUUID", operationSetUUID)
            }
            if (sessionToken != null) {
                pin.put("sessionToken", sessionToken)
            }
            if (command != null) {
                pin.put("command", command)
            }
            return pin.pinInBackground(PIN_NAME).continueWith { pin }
        }

        @JvmStatic
        @JvmOverloads
        fun findAllPinned(excludeUUIDs: Collection<String?>? = null): Task<List<EventuallyPin>> {
            val query = ParseQuery(EventuallyPin::class.java)
                .fromPin(PIN_NAME)
                .ignoreACLs()
                .orderByAscending("time")
            if (excludeUUIDs != null) {
                query.whereNotContainedIn("uuid", excludeUUIDs)
            }

            // We need pass in a null user because we don't want the query to fetch the current user
            // from LDS.
            return query.findInBackground().onSuccessTask { task: Task<List<EventuallyPin>> ->
                val pins = task.result
                val tasks: MutableList<Task<Void>> = ArrayList()
                for (pin in pins) {
                    val `object` = pin.`object`
                    if (`object` != null) {
                        tasks.add(`object`.fetchFromLocalDatastoreAsync<ParseObject>()!!.makeVoid())
                    }
                }
                Task.whenAll(tasks).continueWithTask { Task.forResult(pins) }
            }
        }
    }
}
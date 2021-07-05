/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse

import android.content.Intent
import android.os.Bundle
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException

/**
 * PushRouter handles distribution of push payloads through a broadcast intent with the
 * "com.parse.push.intent.RECEIVE" action. It also serializes a history of the last several pushes
 * seen by this app. This history is necessary for two reasons:
 *
 *
 * - For PPNS, we provide the last-seen timestamp to the server as part of the handshake. This is
 * used as a cursor into the server-side inbox of recent pushes for this client.
 * - For GCM, we use the history to deduplicate pushes when GCM decides to change the canonical
 * registration id for a client (which can result in duplicate pushes while both the old and
 * new registration id are still valid).
 */
class PushRouter private constructor(
    private val diskState: File,
    private val history: PushHistory
) {
    /**
     * Returns the state in this object as a persistable JSONObject. The persisted state looks like
     * this:
     * <p>
     * {
     * "history": {
     * "seen": {
     * "<message ID>": "<timestamp>",
     * ...
     * }
     * "lastTime": "<timestamp>"
     * }
     * }
     */
    /* package */
    @Synchronized
    @Throws(JSONException::class)
    fun toJSON(): JSONObject {
        val json = JSONObject()
        json.put("history", history.toJSON())
        return json
    }

    @Synchronized
    private fun saveStateToDisk() {
        try {
            ParseFileUtils.writeJSONObjectToFile(diskState, toJSON())
        } catch (e: IOException) {
            PLog.e(TAG, "Unexpected error when serializing push state to $diskState", e)
        } catch (e: JSONException) {
            PLog.e(TAG, "Unexpected error when serializing push state to $diskState", e)
        }
    }

    @get:Synchronized
    val lastReceivedTimestamp: String?
        get() = history.lastReceivedTimestamp

    @Synchronized
    fun handlePush(
        pushId: String, timestamp: String, channel: String?, data: JSONObject?
    ): Boolean {
        if (ParseTextUtils.isEmpty(pushId) || ParseTextUtils.isEmpty(timestamp)) {
            return false
        }
        if (!history.tryInsertPush(pushId, timestamp)) {
            return false
        }

        // Persist the fact that we've seen this push.
        saveStateToDisk()
        val extras = Bundle()
        extras.putString(ParsePushBroadcastReceiver.KEY_PUSH_CHANNEL, channel)
        if (data == null) {
            extras.putString(ParsePushBroadcastReceiver.KEY_PUSH_DATA, "{}")
        } else {
            extras.putString(ParsePushBroadcastReceiver.KEY_PUSH_DATA, data.toString())
        }
        val intent = Intent(ParsePushBroadcastReceiver.ACTION_PUSH_RECEIVE)
        intent.putExtras(extras)

        // Set the package name to keep this intent within the given package.
        val context = Parse.getApplicationContext()
        intent.setPackage(context.packageName)
        context.sendBroadcast(intent)
        return true
    }

    companion object {
        private const val TAG = "com.parse.ParsePushRouter"
        private const val LEGACY_STATE_LOCATION = "pushState"
        private const val STATE_LOCATION = "push"
        private const val MAX_HISTORY_LENGTH = 10
        private var instance: PushRouter? = null
        @JvmStatic
        @Synchronized
        fun getInstance(): PushRouter? {
            if (instance == null) {
                val diskState = File(ParsePlugins.get().getFilesDir(), STATE_LOCATION)
                val oldDiskState = File(ParsePlugins.get().getParseDir(), LEGACY_STATE_LOCATION)
                instance = pushRouterFromState(diskState, oldDiskState, MAX_HISTORY_LENGTH)
            }
            return instance
        }

        /* package for tests */
        @Synchronized
        fun resetInstance() {
            ParseFileUtils.deleteQuietly(File(ParsePlugins.get().getFilesDir(), STATE_LOCATION))
            instance = null
        }

        /* package for tests */
        fun pushRouterFromState(
            diskState: File, oldDiskState: File?, maxHistoryLength: Int
        ): PushRouter {
            val state = readJSONFileQuietly(diskState)
            val historyJSON = state?.optJSONObject("history")
            val history = PushHistory(maxHistoryLength, historyJSON)

            // If the deserialized push history object doesn't have a last timestamp, we might have to
            // migrate the last timestamp from the legacy pushState file instead.
            var didMigrate = false
            if (history.lastReceivedTimestamp == null) {
                val oldState = readJSONFileQuietly(oldDiskState)
                if (oldState != null) {
                    history.lastReceivedTimestamp = oldState.optString("lastTime", history.lastReceivedTimestamp)
                    didMigrate = true
                }
            }
            val router = PushRouter(diskState, history)
            if (didMigrate) {
                router.saveStateToDisk()
                ParseFileUtils.deleteQuietly(oldDiskState)
            }
            return router
        }

        private fun readJSONFileQuietly(file: File?): JSONObject? {
            var json: JSONObject? = null
            if (file != null) {
                try {
                    json = ParseFileUtils.readFileToJSONObject(file)
                } catch (e: IOException) {
                    // do nothing
                } catch (e: JSONException) {
                }
            }
            return json
        }
    }
}
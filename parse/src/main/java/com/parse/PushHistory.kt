/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse

import org.json.JSONException
import org.json.JSONObject
import java.util.*

/**
 * PushHistory manages a fixed-length history of pushes received. It is used by to dedup recently
 * received messages, as well as keep track of a last received timestamp that is included in PPNS
 * handshakes.
 */
internal class PushHistory(private val maxHistoryLength: Int, json: JSONObject?) {
    private val entries: PriorityQueue<Entry> = PriorityQueue(maxHistoryLength + 1)
    private val pushIds: HashSet<String> = HashSet(maxHistoryLength + 1)

    /**
     * Returns the last received timestamp, which is always updated whether or not a push was
     * successfully inserted into history.
     */
    var lastReceivedTimestamp: String?

    /**
     * Serializes the history state to a JSON object using the format described in loadJSON().
     */
    @Throws(JSONException::class)
    fun toJSON(): JSONObject {
        val json = JSONObject()
        if (entries.size > 0) {
            val history = JSONObject()
            for (e in entries) {
                history.put(e.pushId, e.timestamp)
            }
            json.put("seen", history)
        }
        json.putOpt("lastTime", lastReceivedTimestamp)
        return json
    }

    /**
     * Attempts to insert a push into history. The push is ignored if we have already seen it
     * recently. Otherwise, the push is inserted into history. If the length of the history exceeds
     * the maximum length, then the history is trimmed by removing the oldest pushes until it no
     * longer exceeds the maximum length.
     *
     * @return Returns whether or not the push was inserted into history.
     */
    fun tryInsertPush(pushId: String, timestamp: String?): Boolean {
        requireNotNull(timestamp) { "Can't insert null pushId or timestamp into history" }
        if (lastReceivedTimestamp == null || timestamp > lastReceivedTimestamp!!) {
            lastReceivedTimestamp = timestamp
        }
        if (pushIds.contains(pushId)) {
            PLog.e(TAG, "Ignored duplicate push $pushId")
            return false
        }
        entries.add(Entry(pushId, timestamp))
        pushIds.add(pushId)
        while (entries.size > maxHistoryLength) {
            val head = entries.remove()
            pushIds.remove(head.pushId)
        }
        return true
    }

    private class Entry(val pushId: String, val timestamp: String) : Comparable<Entry> {
        override fun compareTo(other: Entry): Int {
            return timestamp.compareTo(other.timestamp)
        }
    }

    companion object {
        private const val TAG = "com.parse.PushHistory"
    }

    /**
     * Creates a push history object from a JSON object that looks like this:
     *
     *
     * {
     * "seen": {
     * "push_id_1": "2013-11-01T22:01:00.000Z",
     * "push_id_2": "2013-11-01T22:01:01.000Z",
     * "push_id_3": "2013-11-01T22:01:02.000Z"
     * },
     * "lastTime": "2013-11-01T22:01:02.000Z"
     * }
     *
     *
     * The "history" entries correspond to entries in the "entries" queue.
     * The "lastTime" entry corresponds to the "lastTime" field.
     */
    init {
        lastReceivedTimestamp = null
        if (json != null) {
            val jsonHistory = json.optJSONObject("seen")
            if (jsonHistory != null) {
                val it = jsonHistory.keys()
                while (it.hasNext()) {
                    val pushId = it.next()
                    val timestamp = jsonHistory.optString(pushId)
                    if (pushId != null && timestamp != null) {
                        tryInsertPush(pushId, timestamp)
                    }
                }
            }
            lastReceivedTimestamp = json.optString("lastTime", null)
        }
    }
}
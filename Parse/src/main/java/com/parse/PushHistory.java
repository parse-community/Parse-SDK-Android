/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Iterator;
import java.util.PriorityQueue;

/**
 * PushHistory manages a fixed-length history of pushes received. It is used by to dedup recently
 * received messages, as well as keep track of a last received timestamp that is included in PPNS
 * handshakes.
 */
/** package */ class PushHistory {
  private static final String TAG = "com.parse.PushHistory";
  
  private static class Entry implements Comparable<Entry> {
    public String pushId;
    public String timestamp;
    
    public Entry(String pushId, String timestamp) {
      this.pushId = pushId;
      this.timestamp = timestamp;
    }
    
    @Override
    public int compareTo(Entry other) {
      return timestamp.compareTo(other.timestamp);
    }
  }
  
  private final int maxHistoryLength;
  private final PriorityQueue<Entry> entries;
  private final HashSet<String> pushIds;
  private String lastTime;
  
  /**
   * Creates a push history object from a JSON object that looks like this:
   * 
   * {
   *    "seen": {
   *        "push_id_1": "2013-11-01T22:01:00.000Z",
   *        "push_id_2": "2013-11-01T22:01:01.000Z",
   *        "push_id_3": "2013-11-01T22:01:02.000Z"
   *    },
   *    "lastTime": "2013-11-01T22:01:02.000Z"
   * }
   * 
   * The "history" entries correspond to entries in the "entries" queue.
   * The "lastTime" entry corresponds to the "lastTime" field.
   */
  public PushHistory(int maxHistoryLength, JSONObject json) {
    this.maxHistoryLength = maxHistoryLength;
    this.entries = new PriorityQueue<>(maxHistoryLength + 1);
    this.pushIds = new HashSet<>(maxHistoryLength + 1);
    this.lastTime = null;
    
    if (json != null) {
      JSONObject jsonHistory = json.optJSONObject("seen");
      if (jsonHistory != null) {
        Iterator<String> it = jsonHistory.keys();
        while (it.hasNext()) {
          String pushId = it.next();
          String timestamp = jsonHistory.optString(pushId, null);
          
          if (pushId != null && timestamp != null) {
            tryInsertPush(pushId, timestamp);
          }
        }
      }
      setLastReceivedTimestamp(json.optString("lastTime", null));
    }
  }
  
  /**
   * Serializes the history state to a JSON object using the format described in loadJSON().
   */
  public JSONObject toJSON() throws JSONException {
    JSONObject json = new JSONObject();

    if (entries.size() > 0) {
      JSONObject history = new JSONObject();
      for (Entry e : entries) {
        history.put(e.pushId, e.timestamp);
      }
      json.put("seen", history);
    }

    json.putOpt("lastTime", lastTime);

    return json;
  }

  /**
   * Returns the last received timestamp, which is always updated whether or not a push was
   * successfully inserted into history.
   */
  public String getLastReceivedTimestamp() {
    return lastTime;
  }
  
  public void setLastReceivedTimestamp(String lastTime) {
    this.lastTime = lastTime;
  }

  /**
   * Attempts to insert a push into history. The push is ignored if we have already seen it
   * recently. Otherwise, the push is inserted into history. If the length of the history exceeds
   * the maximum length, then the history is trimmed by removing the oldest pushes until it no
   * longer exceeds the maximum length.
   * 
   * @return Returns whether or not the push was inserted into history.  
   */
  public boolean tryInsertPush(String pushId, String timestamp) {
    if (timestamp == null) {
      throw new IllegalArgumentException("Can't insert null pushId or timestamp into history");
    }
    
    if (lastTime == null || timestamp.compareTo(lastTime) > 0) {
      lastTime = timestamp;
    }

    if (pushIds.contains(pushId)) {
      PLog.e(TAG, "Ignored duplicate push " + pushId);
      return false;
    }

    entries.add(new Entry(pushId, timestamp));
    pushIds.add(pushId);

    while (entries.size() > maxHistoryLength) {
      Entry head = entries.remove();
      pushIds.remove(head.pushId);
    }
    
    return true;
  }
}

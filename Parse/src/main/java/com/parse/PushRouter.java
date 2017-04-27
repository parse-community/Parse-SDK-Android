/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;

/**
 * PushRouter handles distribution of push payloads through a broadcast intent with the
 * "com.parse.push.intent.RECEIVE" action. It also serializes a history of the last several pushes
 * seen by this app. This history is necessary for two reasons:
 *
 *  - For PPNS, we provide the last-seen timestamp to the server as part of the handshake. This is
 *    used as a cursor into the server-side inbox of recent pushes for this client.
 *  - For GCM, we use the history to deduplicate pushes when GCM decides to change the canonical
 *    registration id for a client (which can result in duplicate pushes while both the old and
 *    new registration id are still valid).
 */
/** package */ class PushRouter {
  private static final String TAG = "com.parse.ParsePushRouter";
  private static final String LEGACY_STATE_LOCATION = "pushState";
  private static final String STATE_LOCATION = "push";
  private static final int MAX_HISTORY_LENGTH = 10;

  private static PushRouter instance;
  public static synchronized PushRouter getInstance() {
    if (instance == null) {
      File diskState = new File(ParsePlugins.get().getFilesDir(), STATE_LOCATION);
      File oldDiskState = new File(ParsePlugins.get().getParseDir(), LEGACY_STATE_LOCATION);
      instance = pushRouterFromState(diskState, oldDiskState, MAX_HISTORY_LENGTH);
    }

    return instance;
  }

  /* package for tests */ static synchronized void resetInstance() {
    ParseFileUtils.deleteQuietly(new File(ParsePlugins.get().getFilesDir(), STATE_LOCATION));
    instance = null;
  }

  /* package for tests */ static PushRouter pushRouterFromState(
      File diskState, File oldDiskState, int maxHistoryLength) {
    JSONObject state = readJSONFileQuietly(diskState);
    JSONObject historyJSON = (state != null) ? state.optJSONObject("history") : null;
    PushHistory history = new PushHistory(maxHistoryLength, historyJSON);

    // If the deserialized push history object doesn't have a last timestamp, we might have to
    // migrate the last timestamp from the legacy pushState file instead.
    boolean didMigrate = false;
    if (history.getLastReceivedTimestamp() == null) {
      JSONObject oldState = readJSONFileQuietly(oldDiskState);
      if (oldState != null) {
        String lastTime = oldState.optString("lastTime", null);
        if (lastTime != null) {
          history.setLastReceivedTimestamp(lastTime);
        }
        didMigrate = true;
      }
    }

    PushRouter router = new PushRouter(diskState, history);

    if (didMigrate) {
      router.saveStateToDisk();
      ParseFileUtils.deleteQuietly(oldDiskState);
    }

    return router;
  }

  private static JSONObject readJSONFileQuietly(File file) {
    JSONObject json = null;
    if (file != null) {
      try {
        json = ParseFileUtils.readFileToJSONObject(file);
      } catch (IOException | JSONException e) {
        // do nothing
      }
    }
    return json;
  }

  private final File diskState;
  private final PushHistory history;

  private PushRouter(File diskState, PushHistory history) {
    this.diskState = diskState;
    this.history = history;
  }

  /**
   * Returns the state in this object as a persistable JSONObject. The persisted state looks like
   * this:
   *
   * {
   *   "history": {
   *     "seen": {
   *       "<message ID>": "<timestamp>",
   *       ...
   *     }
   *     "lastTime": "<timestamp>"
   *   }
   * }
   */
  /* package */ synchronized JSONObject toJSON() throws JSONException {
    JSONObject json = new JSONObject();
    json.put("history", history.toJSON());
    return json;
  }

  private synchronized void saveStateToDisk() {
    try {
      ParseFileUtils.writeJSONObjectToFile(diskState, toJSON());
    } catch (IOException | JSONException e) {
      PLog.e(TAG, "Unexpected error when serializing push state to " + diskState, e);
    }
  }

  public synchronized String getLastReceivedTimestamp() {
    return history.getLastReceivedTimestamp();
  }

  public synchronized boolean handlePush(
      String pushId, String timestamp, String channel, JSONObject data) {
    if (ParseTextUtils.isEmpty(pushId) || ParseTextUtils.isEmpty(timestamp)) {
      return false;
    }

    if (!history.tryInsertPush(pushId, timestamp)) {
      return false;
    }

    // Persist the fact that we've seen this push.
    saveStateToDisk();

    Bundle extras = new Bundle();
    extras.putString(ParsePushBroadcastReceiver.KEY_PUSH_CHANNEL, channel);
    if (data == null) {
      extras.putString(ParsePushBroadcastReceiver.KEY_PUSH_DATA, "{}");
    } else {
      extras.putString(ParsePushBroadcastReceiver.KEY_PUSH_DATA, data.toString());
    }

    Intent intent = new Intent(ParsePushBroadcastReceiver.ACTION_PUSH_RECEIVE);
    intent.putExtras(extras);

    // Set the package name to keep this intent within the given package.
    Context context = Parse.getApplicationContext();
    intent.setPackage(context.getPackageName());
    context.sendBroadcast(intent);

    return true;
  }
}

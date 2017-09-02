/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import android.app.Service;
import android.content.Intent;
import android.support.annotation.WorkerThread;

import org.json.JSONException;
import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import bolts.Task;

/**
 * Proxy Service while running in GCM mode.
 *
 * We use an {@link ExecutorService} so that we can operate like a ghetto
 * {@link android.app.IntentService} where all incoming {@link Intent}s will be handled
 * sequentially.
 */
/** package */ class GcmPushHandler implements PushHandler {
  private static final String TAG = "GcmPushHandler";

  static final String REGISTER_RESPONSE_ACTION = "com.google.android.c2dm.intent.REGISTRATION";
  static final String RECEIVE_PUSH_ACTION = "com.google.android.c2dm.intent.RECEIVE";

  GcmPushHandler() {}

  @Override
  public Task<Void> initialize() {
    return GcmRegistrar.getInstance().registerAsync();
  }

  @WorkerThread
  @Override
  public void handlePush(Intent intent) {
    if (intent != null) {
      String action = intent.getAction();
      if (REGISTER_RESPONSE_ACTION.equals(action)) {
        handleGcmRegistrationIntent(intent);
      } else if (RECEIVE_PUSH_ACTION.equals(action)) {
        handleGcmPushIntent(intent);
      } else {
        PLog.e(TAG, "PushService got unknown intent in GCM mode: " + intent);
      }
    }
  }

  @WorkerThread
  private void handleGcmRegistrationIntent(Intent intent) {
    try {
      // Have to block here since we are already in a background thread and as soon as we return,
      // PushService may exit.
      GcmRegistrar.getInstance().handleRegistrationIntentAsync(intent).waitForCompletion();
    } catch (InterruptedException e) {
      // do nothing
    }
  }

  @WorkerThread
  private void handleGcmPushIntent(Intent intent) {
    String messageType = intent.getStringExtra("message_type");
    if (messageType != null) {
      /*
       * The GCM docs reserve the right to use the message_type field for new actions, but haven't
       * documented what those new actions are yet. For forwards compatibility, ignore anything
       * with a message_type field.
       */
      PLog.i(TAG, "Ignored special message type " + messageType + " from GCM via intent " + intent);
    } else {
      String pushId = intent.getStringExtra("push_id");
      String timestamp = intent.getStringExtra("time");
      String dataString = intent.getStringExtra("data");
      String channel = intent.getStringExtra("channel");

      JSONObject data = null;
      if (dataString != null) {
        try {
          data = new JSONObject(dataString);
        } catch (JSONException e) {
          PLog.e(TAG, "Ignoring push because of JSON exception while processing: " + dataString, e);
          return;
        }
      }

      PushRouter.getInstance().handlePush(pushId, timestamp, channel, data);
    }
  }

}

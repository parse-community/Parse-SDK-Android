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
import android.os.IBinder;

import org.json.JSONException;
import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Proxy Service while running in GCM mode.
 *
 * We use an {@link ExecutorService} so that we can operate like a ghetto
 * {@link android.app.IntentService} where all incoming {@link Intent}s will be handled
 * sequentially.
 */
/** package */ class GCMService implements ProxyService {
  private static final String TAG = "GCMService";

  public static final String REGISTER_RESPONSE_ACTION =
      "com.google.android.c2dm.intent.REGISTRATION";
  public static final String RECEIVE_PUSH_ACTION =
      "com.google.android.c2dm.intent.RECEIVE";

  private final WeakReference<Service> parent;
  private ExecutorService executor;

  /* package */ GCMService(Service parent) {
    this.parent = new WeakReference<>(parent);
  }

  @Override
  public void onCreate() {
    executor = Executors.newSingleThreadExecutor();
  }

  @Override
  public void onDestroy() {
    if (executor != null) {
      executor.shutdown();
      executor = null;
    }
  }

  @Override
  public int onStartCommand(final Intent intent, int flags, final int startId) {
    executor.execute(new Runnable() {
      @Override
      public void run() {
        try {
          onHandleIntent(intent);
        } finally {
          ServiceUtils.completeWakefulIntent(intent);
          stopParent(startId); // automatically stops service if this is the last outstanding task
        }
      }
    });

    return Service.START_NOT_STICKY;
  }

  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }

  private void onHandleIntent(Intent intent) {
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

  private void handleGcmRegistrationIntent(Intent intent) {
    try {
      // Have to block here since GCMService is basically an IntentService, and the service is
      // may exit before async handling of the registration is complete if we don't wait for it to
      // complete.
      GcmRegistrar.getInstance().handleRegistrationIntentAsync(intent).waitForCompletion();
    } catch (InterruptedException e) {
      // do nothing
    }
  }

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

  /**
   * Stop the parent Service, if we're still running.
   */
  private void stopParent(int startId) {
    Service p = parent.get();
    if (p != null) {
      p.stopSelf(startId);
    }
  }
}

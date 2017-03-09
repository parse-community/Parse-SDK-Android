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
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
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
  public static final String INSTANCE_ID_ACTION =
      "com.google.android.gms.iid.InstanceID";

  private static final String REGISTRATION_ID_EXTRA = "registration_id";
  private static final String GCM_BODY_EXTRA = "gcm.notification.body";
  private static final String GCM_TITLE_EXTRA = "gcm.notification.title";
  private static final String GCM_SOUND_EXTRA = "gcm.notification.sound";
  private static final String GCM_COMPOSE_ID_EXTRA = "google.c.a.c_id";
  private static final String GCM_COMPOSE_TIMESTAMP_EXTRA = "google.c.a.ts";

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
      } else if (INSTANCE_ID_ACTION.equals(action)) {
        handleInvalidatedInstanceId(intent);
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
      PLog.d(TAG, "Got registration intent in service");
      String registrationId = intent.getStringExtra(REGISTRATION_ID_EXTRA);
      // Multiple IDs come back to the legacy intnet-based API with an |ID|num: prefix; cut it off.
      if (registrationId.startsWith("|ID|")) {
        registrationId = registrationId.substring(registrationId.indexOf(':') + 1);
      }
      GcmRegistrar.getInstance().setGCMRegistrationId(registrationId).waitForCompletion();
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
      try {
        if (dataString != null) {
            data = new JSONObject(dataString);
        } else if (pushId == null && timestamp == null && dataString == null && channel == null) {
          // The Parse SDK is older than GCM, so it has some non-standard payload fields.
          // This allows the Parse SDK to handle push providers using the now-standard GCM payloads.
          pushId = intent.getStringExtra(GCM_COMPOSE_ID_EXTRA);
          String millisString = intent.getStringExtra(GCM_COMPOSE_TIMESTAMP_EXTRA);
          if (millisString != null) {
            Long millis = Long.valueOf(millisString);
            DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mmZ");
            timestamp = df.format(new Date(millis));
          }
          data = new JSONObject();
          if (intent.hasExtra(GCM_BODY_EXTRA)) {
            data.put("alert", intent.getStringExtra(GCM_BODY_EXTRA));
          }
          if (intent.hasExtra(GCM_TITLE_EXTRA)) {
            data.put("title", intent.getStringExtra(GCM_TITLE_EXTRA));
          }
          if (intent.hasExtra(GCM_SOUND_EXTRA)) {
            data.put("sound", intent.getStringExtra(GCM_SOUND_EXTRA));
          }

          String from = intent.getStringExtra("from");
          if (from != null && from.startsWith("/topics/")) {
            channel = from.substring("/topics/".length());
          }
        }
      } catch (JSONException e) {
        PLog.e(TAG, "Ignoring push because of JSON exception while processing: " + dataString, e);
        return;
      }

      PushRouter.getInstance().handlePush(pushId, timestamp, channel, data);
    }
  }

  private void handleInvalidatedInstanceId(Intent intent) {
    try {
      GcmRegistrar.getInstance().sendRegistrationRequestAsync().waitForCompletion();
    } catch (InterruptedException e) {
      // do nothing
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

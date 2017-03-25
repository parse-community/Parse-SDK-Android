/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.SystemClock;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import bolts.Continuation;
import bolts.Task;
import bolts.TaskCompletionSource;

/**
 * A class that manages registering for GCM and updating the registration if it is out of date.
 */
/** package */ class GcmRegistrar {
  private static final String TAG = "com.parse.GcmRegistrar";
  private static final String REGISTRATION_ID_EXTRA = "registration_id";
  private static final String ERROR_EXTRA = "error";

  private static final String SENDER_ID_EXTRA = "com.parse.push.gcm_sender_id";

  public static final String REGISTER_ACTION = "com.google.android.c2dm.intent.REGISTER";

  private static final String FILENAME_DEVICE_TOKEN_LAST_MODIFIED = "deviceTokenLastModified";
  private long localDeviceTokenLastModified;
  private final Object localDeviceTokenLastModifiedMutex = new Object();

  public static GcmRegistrar getInstance() {
    return Singleton.INSTANCE;
  }

  private static class Singleton {
    public static final GcmRegistrar INSTANCE = new GcmRegistrar(Parse.getApplicationContext());
  }

  private static String actualSenderIDFromExtra(Object senderIDExtra) {
    if (!(senderIDExtra instanceof String)) {
      return null;
    }

    String senderID = (String)senderIDExtra;
    if (!senderID.startsWith("id:")) {
      return null;
    }

    return senderID.substring(3);
  }

  private final Object lock = new Object();
  private Request request = null;
  private Context context = null;

  // This a package-level constructor for unit testing only. Otherwise, use getInstance().
  /* package */ GcmRegistrar(Context context) {
    this.context = context;
  }

  /**
   * Does nothing if the client already has a valid GCM registration id. Otherwise, sends out a
   * GCM registration request and saves the resulting registration id to the server via
   * ParseInstallation.
   */
  public Task<Void> registerAsync() {
    if (ManifestInfo.getPushType() != PushType.GCM) {
      return Task.forResult(null);
    }
    synchronized (lock) {
      /*
       * If we don't yet have a device token, mark this installation as wanting to use GCM by
       * setting its pushType to GCM. If the registration does not succeed (because the device
       * is offline, for instance), then update() will re-register for a GCM device token at
       * next app initialize time.
       */
      final ParseInstallation installation = ParseInstallation.getCurrentInstallation();
      // Check whether we need to send registration request, if installation does not
      // have device token or local device token is stale, we need to send request.
      Task<Boolean> checkTask = installation.getDeviceToken() == null
          ? Task.forResult(true)
          : isLocalDeviceTokenStaleAsync();
      return checkTask.onSuccessTask(new Continuation<Boolean, Task<Void>>() {
        @Override
        public Task<Void> then(Task<Boolean> task) throws Exception {
          if (!task.getResult()) {
            return Task.forResult(null);
          }
          if (installation.getPushType() != PushType.GCM) {
            installation.setPushType(PushType.GCM);
          }
          // We do not need to wait sendRegistrationRequestAsync, since this task will finish
          // after we get the response from GCM, if we wait for this task, it will block our test.
          sendRegistrationRequestAsync();
          return Task.forResult(null);
        }
      });
    }
  }

  private Task<Void> sendRegistrationRequestAsync() {
    synchronized (lock) {
      if (request != null) {
        return Task.forResult(null);
      }
      // Look for an element like this as a child of the <application> element:
      //
      //   <meta-data android:name="com.parse.push.gcm_sender_id"
      //              android:value="id:567327206255" />
      //
      // The reason why the "id:" prefix is necessary is because Android treats any metadata value
      // that is a string of digits as an integer. So the call to Bundle.getString() will actually
      // return null for `android:value="567327206255"`. Additionally, Bundle.getInteger() returns
      // a 32-bit integer. For `android:value="567327206255"`, this returns a truncated integer
      // because 567327206255 is larger than the largest 32-bit integer.
      Bundle metaData = ManifestInfo.getApplicationMetadata(context);
      String senderID = null;

      if (metaData != null) {
        Object senderIDExtra = metaData.get(SENDER_ID_EXTRA);

        if (senderIDExtra != null) {
          senderID = actualSenderIDFromExtra(senderIDExtra);

          if (senderID == null) {
            PLog.e(TAG, "Found " + SENDER_ID_EXTRA + " <meta-data> element with value \"" +
                senderIDExtra.toString() + "\", but the value is missing the expected \"id:\" " +
                "prefix.");
            return null;
          }
        }
      }

      if (senderID == null) {
          PLog.e(TAG, "You must provide " + SENDER_ID_EXTRA + " in your AndroidManifest.xml\n" +
                  "Make sure to prefix with the value with id:\n\n" +
                  "<meta-data\n" +
                  "    android:name=\"com.parse.push.gcm_sender_id\"\n" +
                  "    android:value=\"id:<YOUR_GCM_SENDER_ID>\" />");
        return null;
      }

      request = Request.createAndSend(context, senderID);
      return request.getTask().continueWith(new Continuation<String, Void>() {
        @Override
        public Void then(Task<String> task) {
          Exception e = task.getError();
          if (e != null) {
            PLog.e(TAG, "Got error when trying to register for GCM push", e);
          }

          synchronized (lock) {
            request = null;
          }

          return null;
        }
      });
    }
  }

  /**
   * Should be called by a broadcast receiver or service to handle the GCM registration response
   * intent (com.google.android.c2dm.intent.REGISTRATION).
   */
  public Task<Void> handleRegistrationIntentAsync(Intent intent) {
    List<Task<Void>> tasks = new ArrayList<>();
    /*
     * We have to parse the response here because GCM may send us a new registration_id
     * out-of-band without a request in flight.
     */
    String registrationId = intent.getStringExtra(REGISTRATION_ID_EXTRA);

    if (registrationId != null && registrationId.length() > 0) {
      PLog.v(TAG, "Received deviceToken <" + registrationId + "> from GCM.");

      ParseInstallation installation = ParseInstallation.getCurrentInstallation();
      // Compare the new deviceToken with the old deviceToken, we only update the
      // deviceToken if the new one is different from the old one. This does not follow google
      // guide strictly. But we find most of the time if user just update the app, the
      // registrationId does not change so there is no need to save it again.
      if (!registrationId.equals(installation.getDeviceToken())) {
        installation.setPushType(PushType.GCM);
        installation.setDeviceToken(registrationId);
        tasks.add(installation.saveInBackground());
      }
      // We need to update the last modified even the deviceToken is the same. Otherwise when the
      // app is opened again, isDeviceTokenStale() will always return false so we will send
      // request to GCM every time.
      tasks.add(updateLocalDeviceTokenLastModifiedAsync());
    }
    synchronized (lock) {
      if (request != null) {
        request.onReceiveResponseIntent(intent);
      }
    }
    return Task.whenAll(tasks);
  }

  // Only used by tests.
  /* package */ int getRequestIdentifier() {
    synchronized (lock) {
      return request != null ? request.identifier : 0;
    }
  }

  /** package for tests */ Task<Boolean> isLocalDeviceTokenStaleAsync() {
    return getLocalDeviceTokenLastModifiedAsync().onSuccessTask(new Continuation<Long, Task<Boolean>>() {
      @Override
      public Task<Boolean> then(Task<Long> task) throws Exception {
        long localDeviceTokenLastModified = task.getResult();
        return Task.forResult(localDeviceTokenLastModified != ManifestInfo.getLastModified());
      }
    });
  }

  /** package for tests */ Task<Void> updateLocalDeviceTokenLastModifiedAsync() {
    return Task.call(new Callable<Void>() {
      @Override
      public Void call() throws Exception {
        synchronized (localDeviceTokenLastModifiedMutex) {
          localDeviceTokenLastModified = ManifestInfo.getLastModified();
          final String localDeviceTokenLastModifiedStr =
              String.valueOf(localDeviceTokenLastModified);
          try {
            ParseFileUtils.writeStringToFile(getLocalDeviceTokenLastModifiedFile(),
                localDeviceTokenLastModifiedStr, "UTF-8");
          } catch (IOException e) {
            // do nothing
          }
        }
        return null;
      }
    }, Task.BACKGROUND_EXECUTOR);
  }

  private Task<Long> getLocalDeviceTokenLastModifiedAsync() {
    return Task.call(new Callable<Long>() {
      @Override
      public Long call() throws Exception {
        synchronized (localDeviceTokenLastModifiedMutex) {
          if (localDeviceTokenLastModified == 0) {
            try {
              String localDeviceTokenLastModifiedStr = ParseFileUtils.readFileToString(
                  getLocalDeviceTokenLastModifiedFile(), "UTF-8");
              localDeviceTokenLastModified = Long.valueOf(localDeviceTokenLastModifiedStr);
            } catch (IOException e) {
              localDeviceTokenLastModified = 0;
            }
          }
          return localDeviceTokenLastModified;
        }
      }
    }, Task.BACKGROUND_EXECUTOR);
  }

  /** package for tests */ static File getLocalDeviceTokenLastModifiedFile() {
    File dir = Parse.getParseCacheDir("GCMRegistrar");
    return new File(dir, FILENAME_DEVICE_TOKEN_LAST_MODIFIED);
  }

  /** package for tests */ static void deleteLocalDeviceTokenLastModifiedFile() {
    ParseFileUtils.deleteQuietly(getLocalDeviceTokenLastModifiedFile());
  }

  /**
   * Encapsulates the a GCM registration request-response, potentially using AlarmManager to
   * schedule retries if the GCM service is not available.
   */
  private static class Request {
    private static final String RETRY_ACTION = "com.parse.RetryGcmRegistration";
    private static final int MAX_RETRIES = 5;
    private static final int BACKOFF_INTERVAL_MS = 3000;

    final private Context context;
    final private String senderId;
    final private Random random;
    final private int identifier;
    final private TaskCompletionSource<String> tcs;
    final private PendingIntent appIntent;
    final private AtomicInteger tries;
    final private PendingIntent retryIntent;
    final private BroadcastReceiver retryReceiver;

    public static Request createAndSend(Context context, String senderId) {
      Request request = new Request(context, senderId);
      request.send();

      return request;
    }

    private Request(Context context, String senderId) {
      this.context = context;
      this.senderId = senderId;
      this.random = new Random();
      this.identifier = this.random.nextInt();
      this.tcs = new TaskCompletionSource<>();
      this.appIntent = PendingIntent.getBroadcast(this.context, identifier, new Intent(), 0);
      this.tries = new AtomicInteger(0);

      String packageName = this.context.getPackageName();
      Intent intent = new Intent(RETRY_ACTION).setPackage(packageName);
      intent.addCategory(packageName);
      intent.putExtra("random", identifier);
      this.retryIntent = PendingIntent.getBroadcast(this.context, identifier, intent, 0);

      this.retryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
          if (intent != null && intent.getIntExtra("random", 0) == identifier) {
            send();
          }
        }
      };

      IntentFilter filter = new IntentFilter();
      filter.addAction(RETRY_ACTION);
      filter.addCategory(packageName);

      context.registerReceiver(this.retryReceiver, filter);
    }

    public Task<String> getTask() {
      return tcs.getTask();
    }

    private void send() {
      Intent intent = new Intent(REGISTER_ACTION);
      intent.setPackage("com.google.android.gsf");
      intent.putExtra("sender", senderId);
      intent.putExtra("app", appIntent);

      ComponentName name = null;
      try {
        name = context.startService(intent);
      } catch (SecurityException exception) {
        // do nothing
      }

      if (name == null) {
        finish(null, "GSF_PACKAGE_NOT_AVAILABLE");
      }

      tries.incrementAndGet();

      PLog.v(TAG, "Sending GCM registration intent");
    }

    public void onReceiveResponseIntent(Intent intent) {
      String registrationId = intent.getStringExtra(REGISTRATION_ID_EXTRA);
      String error = intent.getStringExtra(ERROR_EXTRA);

      if (registrationId == null && error == null) {
        PLog.e(TAG, "Got no registration info in GCM onReceiveResponseIntent");
        return;
      }

      // Retry with exponential backoff if GCM isn't available.
      if ("SERVICE_NOT_AVAILABLE".equals(error) && tries.get() < MAX_RETRIES) {
        AlarmManager manager = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        int alarmType = AlarmManager.ELAPSED_REALTIME_WAKEUP;
        long delay = (1 << tries.get()) * BACKOFF_INTERVAL_MS + random.nextInt(BACKOFF_INTERVAL_MS);
        long start = SystemClock.elapsedRealtime() + delay;
        manager.set(alarmType, start, retryIntent);
      } else {
        finish(registrationId, error);
      }
    }

    private void finish(String registrationId, String error) {
      boolean didSetResult;

      if (registrationId != null) {
        didSetResult = tcs.trySetResult(registrationId);
      } else {
        didSetResult = tcs.trySetError(new Exception("GCM registration error: " + error));
      }

      if (didSetResult) {
        appIntent.cancel();
        retryIntent.cancel();
        context.unregisterReceiver(this.retryReceiver);
      }
    }
  }
}

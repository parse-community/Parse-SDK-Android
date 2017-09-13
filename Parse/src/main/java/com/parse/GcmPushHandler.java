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
import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
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

  @NonNull
  @Override
  public SupportLevel isSupported() {
    if (!ManifestInfo.isGooglePlayServicesAvailable()) {
      return SupportLevel.MISSING_REQUIRED_DECLARATIONS;
    }
    return getManifestSupportLevel();
  }

  private SupportLevel getManifestSupportLevel() {
    Context context = Parse.getApplicationContext();
    String[] requiredPermissions = new String[] {
        "android.permission.INTERNET",
        "android.permission.ACCESS_NETWORK_STATE",
        "android.permission.WAKE_LOCK",
        "com.google.android.c2dm.permission.RECEIVE",
        context.getPackageName() + ".permission.C2D_MESSAGE"
    };

    if (!ManifestInfo.hasRequestedPermissions(context, requiredPermissions)) {
      return SupportLevel.MISSING_REQUIRED_DECLARATIONS;
    }

    String packageName = context.getPackageName();
    String rcvrPermission = "com.google.android.c2dm.permission.SEND";
    Intent[] intents = new Intent[] {
        new Intent(GcmPushHandler.RECEIVE_PUSH_ACTION)
            .setPackage(packageName)
            .addCategory(packageName),
        new Intent(GcmPushHandler.REGISTER_RESPONSE_ACTION)
            .setPackage(packageName)
            .addCategory(packageName),
    };

    if (!ManifestInfo.checkReceiver(GcmBroadcastReceiver.class, rcvrPermission, intents)) {
      return SupportLevel.MISSING_REQUIRED_DECLARATIONS;
    }

    String[] optionalPermissions = new String[] {
        "android.permission.VIBRATE"
    };

    if (!ManifestInfo.hasGrantedPermissions(context, optionalPermissions)) {
      return SupportLevel.MISSING_OPTIONAL_DECLARATIONS;
    }

    return SupportLevel.SUPPORTED;
  }

  @Nullable
  @Override
  public String getWarningMessage(SupportLevel level) {
    switch (level) {
      case SUPPORTED: return null;
      case MISSING_OPTIONAL_DECLARATIONS: return "Using GCM for Parse Push, " +
          "but the app manifest is missing some optional " +
          "declarations that should be added for maximum reliability. Please " +
          getWarningMessage();
      case MISSING_REQUIRED_DECLARATIONS:
        if (ManifestInfo.isGooglePlayServicesAvailable()) {
          return "Cannot use GCM for push because the app manifest is missing some " +
              "required declarations. Please " + getWarningMessage();
        } else {
          return "Cannot use GCM for push on this device because Google Play " +
              "Services is not available. Install Google Play Services from the Play Store.";
        }
    }
    return null;
  }

  static String getWarningMessage() {
    String packageName = Parse.getApplicationContext().getPackageName();
    String gcmPackagePermission = packageName + ".permission.C2D_MESSAGE";
    return "make sure that these permissions are declared as children " +
              "of the root <manifest> element:\n" +
              "\n" +
              "<uses-permission android:name=\"android.permission.INTERNET\" />\n" +
              "<uses-permission android:name=\"android.permission.ACCESS_NETWORK_STATE\" />\n" +
              "<uses-permission android:name=\"android.permission.VIBRATE\" />\n" +
              "<uses-permission android:name=\"android.permission.WAKE_LOCK\" />\n" +
              "<uses-permission android:name=\"android.permission.GET_ACCOUNTS\" />\n" +
              "<uses-permission android:name=\"com.google.android.c2dm.permission.RECEIVE\" />\n" +
              "<permission android:name=\"" + gcmPackagePermission + "\" " +
              "android:protectionLevel=\"signature\" />\n" +
              "<uses-permission android:name=\"" + gcmPackagePermission + "\" />\n" +
              "\n" +
              "Also, please make sure that these services and broadcast receivers are declared as " +
              "children of the <application> element:\n" +
              "\n" +
              "<service android:name=\"com.parse.PushService\" />\n" +
              "<receiver android:name=\"com.parse.GcmBroadcastReceiver\" " +
              "android:permission=\"com.google.android.c2dm.permission.SEND\">\n" +
              "  <intent-filter>\n" +
              "    <action android:name=\"com.google.android.c2dm.intent.RECEIVE\" />\n" +
              "    <action android:name=\"com.google.android.c2dm.intent.REGISTRATION\" />\n" +
              "    <category android:name=\"" + packageName + "\" />\n" +
              "  </intent-filter>\n" +
              "</receiver>\n" +
              "<receiver android:name=\"com.parse.ParsePushBroadcastReceiver\"" +
              " android:exported=false>\n" +
              "  <intent-filter>\n" +
              "    <action android:name=\"com.parse.push.intent.RECEIVE\" />\n" +
              "    <action android:name=\"com.parse.push.intent.OPEN\" />\n" +
              "    <action android:name=\"com.parse.push.intent.DELETE\" />\n" +
              "  </intent-filter>\n" +
              "</receiver>";
  }

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

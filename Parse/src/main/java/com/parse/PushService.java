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
import android.os.IBinder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import bolts.Task;

/**
 * A service to listen for push notifications. This operates in the same process as the parent
 * application.
 * <p/>
 * The {@code PushService} can listen to pushes from Google Cloud Messaging (GCM).
 * To configure the {@code PushService} for GCM, ensure these permission declarations are present in
 * your AndroidManifest.xml as children of the <code>&lt;manifest&gt;</code> element:
 * <p/>
 * <pre>
 * &lt;uses-permission android:name="android.permission.INTERNET" /&gt;
 * &lt;uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" /&gt;
 * &lt;uses-permission android:name="android.permission.VIBRATE" /&gt;
 * &lt;uses-permission android:name="android.permission.WAKE_LOCK" /&gt;
 * &lt;uses-permission android:name="android.permission.GET_ACCOUNTS" /&gt;
 * &lt;uses-permission android:name="com.google.android.c2dm.permission.RECEIVE" /&gt;
 * &lt;permission android:name="YOUR_PACKAGE_NAME.permission.C2D_MESSAGE"
 *   android:protectionLevel="signature" /&gt;
 * &lt;uses-permission android:name="YOUR_PACKAGE_NAME.permission.C2D_MESSAGE" /&gt;
 * </pre>
 * <p/>
 * Replace YOUR_PACKAGE_NAME in the declarations above with your application's package name. Also,
 * make sure that {@link GcmBroadcastReceiver}, {@link PushService} and
 * {@link ParsePushBroadcastReceiver} are declared as children of the
 * <code>&lt;application&gt;</code> element:
 * <p/>
 * <pre>
 * &lt;service android:name="com.parse.PushService" /&gt;
 * &lt;receiver android:name="com.parse.GcmBroadcastReceiver"
 *  android:permission="com.google.android.c2dm.permission.SEND"&gt;
 *   &lt;intent-filter&gt;
 *     &lt;action android:name="com.google.android.c2dm.intent.RECEIVE" /&gt;
 *     &lt;action android:name="com.google.android.c2dm.intent.REGISTRATION" /&gt;
 *     &lt;category android:name="YOUR_PACKAGE_NAME" /&gt;
 *   &lt;/intent-filter&gt;
 * &lt;/receiver&gt;
 * &lt;receiver android:name="com.parse.ParsePushBroadcastReceiver" android:exported=false&gt;
 *  &lt;intent-filter&gt;
 *     &lt;action android:name="com.parse.push.intent.RECEIVE" /&gt;
 *     &lt;action android:name="com.parse.push.intent.OPEN" /&gt;
 *     &lt;action android:name="com.parse.push.intent.DELETE" /&gt;
 *   &lt;/intent-filter&gt;
 * &lt;/receiver&gt;
 * </pre>
 * <p/>
 * Again, replace YOUR_PACKAGE_NAME with your application's package name.
 * If you want to customize the way your app generates Notifications for your pushes, you
 * can register a custom subclass of {@link ParsePushBroadcastReceiver}.
 * <p/>
 * Once push notifications are configured in the manifest, you can subscribe to a push channel by
 * calling:
 * <p/>
 * <pre>
 * ParsePush.subscribeInBackground(&quot;the_channel_name&quot;);
 * </pre>
 * <p/>
 * When the client receives a push message, a notification will appear in the system tray. When the
 * user taps the notification, it will broadcast the &quot;com.parse.push.intent.OPEN&quot; intent.
 * The {@link ParsePushBroadcastReceiver} listens to this intent to track an app open event and
 * launch the app's launcher activity. To customize this behavior override
 * {@link ParsePushBroadcastReceiver#onPushOpen(Context, Intent)}.
 */
public final class PushService extends Service {
  private static final String TAG = "com.parse.PushService";

  //region ServiceLifecycleCallbacks used for testing

  private static List<ServiceLifecycleCallbacks> serviceLifecycleCallbacks = null;
  
  /* package */ interface ServiceLifecycleCallbacks {
    void onServiceCreated(Service service);
    void onServiceDestroyed(Service service);
  }

  /* package */ static void registerServiceLifecycleCallbacks(ServiceLifecycleCallbacks callbacks) {
    synchronized (PushService.class) {
      if (serviceLifecycleCallbacks == null) {
        serviceLifecycleCallbacks = new ArrayList<>();
      }
      serviceLifecycleCallbacks.add(callbacks);
    }
  }

  /* package */ static void unregisterServiceLifecycleCallbacks(
      ServiceLifecycleCallbacks callbacks) {
    synchronized (PushService.class) {
      serviceLifecycleCallbacks.remove(callbacks);
      if (serviceLifecycleCallbacks.size() <= 0) {
        serviceLifecycleCallbacks = null;
      }
    }
  }

  private static void dispatchOnServiceCreated(Service service) {
    Object[] callbacks = collectServiceLifecycleCallbacks();
    if (callbacks != null) {
      for (Object callback : callbacks) {
        ((ServiceLifecycleCallbacks) callback).onServiceCreated(service);
      }
    }
  }

  private static void dispatchOnServiceDestroyed(Service service) {
    Object[] callbacks = collectServiceLifecycleCallbacks();
    if (callbacks != null) {
      for (Object callback : callbacks) {
        ((ServiceLifecycleCallbacks) callback).onServiceDestroyed(service);
      }
    }
  }

  private static Object[] collectServiceLifecycleCallbacks() {
    Object[] callbacks = null;
    synchronized (PushService.class) {
      if (serviceLifecycleCallbacks == null) {
        return null;
      }
      if (serviceLifecycleCallbacks.size() > 0) {
        callbacks = serviceLifecycleCallbacks.toArray();
      }
    }
    return callbacks;
  }

  //endregion

  // We delegate the intent to a PushHandler running in a streamlined executor.
  private ExecutorService executor;
  private PushHandler handler;

  /**
   * Client code should not construct a PushService directly.
   */
  public PushService() {
    super();
  }

  static PushHandler createPushHandler() {
    return PushHandler.Factory.create(ManifestInfo.getPushType());
  }

  // For tests
  void setPushHandler(PushHandler handler) {
    this.handler = handler;
  }

  /**
   * Called at startup at the moment of parsing the manifest, to see
   * if it was correctly set-up.
   */
  static boolean isSupported() {
    return ManifestInfo.getServiceInfo(PushService.class) != null;
  }

  static Task<Void> initialize() {
    // Some handlers might need initialization.
    return createPushHandler().initialize();
  }

  /**
   * Client code should not call {@code onCreate} directly.
   */
  @Override
  public void onCreate() {
    super.onCreate();
    if (ParsePlugins.Android.get() == null) {
      PLog.e(TAG, "The Parse push service cannot start because Parse.initialize "
          + "has not yet been called. If you call Parse.initialize from "
          + "an Activity's onCreate, that call should instead be in the "
          + "Application.onCreate. Be sure your Application class is registered "
          + "in your AndroidManifest.xml with the android:name property of your "
          + "<application> tag.");
      stopSelf();
      return;
    }

    executor = Executors.newSingleThreadExecutor();
    handler = createPushHandler();
    dispatchOnServiceCreated(this);
  }

  /**
   * Client code should not call {@code onStartCommand} directly.
   */
  @Override
  public int onStartCommand(final Intent intent, int flags, final int startId) {
    if (ManifestInfo.getPushType() == PushType.NONE) {
      PLog.e(TAG, "Started push service even though no push service is enabled: " + intent);
    }

    executor.execute(new Runnable() {
      @Override
      public void run() {
        try {
          handler.handlePush(intent);
        } finally {
          ServiceUtils.completeWakefulIntent(intent);
          stopSelf(startId);
        }
      }
    });

    return START_NOT_STICKY;
  }

  /**
   * Client code should not call {@code onBind} directly.
   */
  @Override
  public IBinder onBind(Intent intent) {
    throw new IllegalArgumentException("You cannot bind directly to the PushService. "
        + "Use PushService.subscribe instead.");
  }

  /**
   * Client code should not call {@code onDestroy} directly.
   */
  @Override
  public void onDestroy() {
    if (executor != null) {
      executor.shutdown();
      executor = null;
      handler = null;
    }

    dispatchOnServiceDestroyed(this);
    super.onDestroy();
  }
}

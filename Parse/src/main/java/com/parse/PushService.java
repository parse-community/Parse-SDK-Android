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

/**
 * A service to listen for push notifications. This operates in the same process as the parent
 * application.
 * <p/>
 * The {@code PushService} can listen to pushes from two different sources: Google Cloud Messaging
 * (GCM) or Parse's own push network. Parse will inspect your application's manifest at runtime and
 * determine which service to use for push. We recommend using GCM for push on devices that have
 * Google Play Store support. Parse uses its own push network for apps that want to avoid a
 * dependency on the Google Play Store, and for devices (like Kindles) which do not have Play Store
 * support.
 * <p/>
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
 * make sure that com.parse.GcmBroadcastReceiver and com.parse.PushService are declared as children
 * of the <code>&lt;application&gt;</code> element:
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
 * </pre>
 * <p/>
 * Again, replace YOUR_PACKAGE_NAME with your application's package name.
 * <p/>
 * To configure the PushService for Parse's push network, ensure these permission declarations are
 * present in your AndroidManifest.xml as children of the <code>&lt;manifest&gt;</code> element:
 * <p/>
 * <pre>
 * &lt;uses-permission android:name="android.permission.INTERNET" /&gt;
 * &lt;uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" /&gt;
 * &lt;uses-permission android:name="android.permission.VIBRATE" /&gt;
 * &lt;uses-permission android:name="android.permission.WAKE_LOCK" /&gt;
 * </pre>
 * <p/>
 * Also, make sure that {@link PushService} and {@link ParsePushBroadcastReceiver} are declared as
 * children of the <code>&lt;application&gt;</code> element:
 * <p/>
 * <pre>
 * &lt;service android:name="com.parse.PushService" /&gt;
 * &lt;receiver android:name="com.parse.ParsePushBroadcastReceiver" android:exported=false&gt;
 *  &lt;intent-filter&gt;
 *     &lt;action android:name="com.parse.push.intent.RECEIVE" /&gt;
 *     &lt;action android:name="com.parse.push.intent.OPEN" /&gt;
 *     &lt;action android:name="com.parse.push.intent.DELETE" /&gt;
 *   &lt;/intent-filter&gt;
 * &lt;/receiver&gt;
 * </pre>
 * <p/>
 * Note that you can configure the push service for both GCM and Parse's network by adding all the
 * declarations above to your application's manifest. In this case, Parse will use GCM on devices
 * with Play Store support and fall back to using Parse's network on devices without Play Store
 * support. If you want to customize the way your app generates Notifications for your pushes, you
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

  /* package */ static final String ACTION_START_IF_REQUIRED =
      "com.parse.PushService.startIfRequired";

  private static boolean loggedStartError = false;

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
  
  /**
   * Client code should not construct a PushService directly.
   */
  public PushService() {
    super();
  }

  /* package */ static void startServiceIfRequired(Context context) {
    switch (ManifestInfo.getPushType()) {
      case PPNS:
        ParseInstallation installation = ParseInstallation.getCurrentInstallation();

        // If we need to downgrade the installation from GCM to PPNS, make sure to clear out the GCM
        // info in the installation and send it back up to the server.
        if (installation.getPushType() == PushType.GCM) {
          PLog.w(TAG, "Detected a client that used to use GCM and is now using PPNS.");

          installation.removePushType();
          installation.removeDeviceToken();
          installation.saveEventually();
        }

        ServiceUtils.runIntentInService(
            context, new Intent(PushService.ACTION_START_IF_REQUIRED), PushService.class);
        break;
      case GCM:
        GcmRegistrar.getInstance().registerAsync();
        break;
      default:
        if (!loggedStartError) {
          PLog.e(TAG, "Tried to use push, but this app is not configured for push due to: " +
              ManifestInfo.getNonePushTypeLogMessage());
          loggedStartError = true;
        }
        break;
    }
  }

  /* package */ static void stopServiceIfRequired(Context context) {
    switch (ManifestInfo.getPushType()) {
      case PPNS:
        context.stopService(new Intent(context, PushService.class));
        break;
      // We don't need to stop anything for GCM since PushService will only be short lived
    }
  }

  private ProxyService proxy;

  /**
   * Client code should not call {@code onCreate} directly.
   */
  @Override
  public void onCreate() {
    super.onCreate();
    if (ParsePlugins.Android.get().applicationContext() == null) {
      PLog.e(TAG, "The Parse push service cannot start because Parse.initialize "
          + "has not yet been called. If you call Parse.initialize from "
          + "an Activity's onCreate, that call should instead be in the "
          + "Application.onCreate. Be sure your Application class is registered "
          + "in your AndroidManifest.xml with the android:name property of your "
          + "<application> tag.");
      stopSelf();
      return;
    }
    
    switch (ManifestInfo.getPushType()) {
      case PPNS:
        proxy = PPNSUtil.newPPNSService(this);
        break;
      case GCM:
        proxy = new GCMService(this);
        break;
      default:
        PLog.e(TAG, "PushService somehow started even though this device doesn't support push.");
        break;
    }

    if (proxy != null) {
      proxy.onCreate();
    }

    dispatchOnServiceCreated(this);
  }

  /**
   * Client code should not call {@code onStartCommand} directly.
   */
  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    switch (ManifestInfo.getPushType()) {
      case PPNS:
      case GCM:
        return proxy.onStartCommand(intent, flags, startId);
      default:
        PLog.e(TAG, "Started push service even though no push service is enabled: " + intent);
        ServiceUtils.completeWakefulIntent(intent);
        return START_NOT_STICKY;
    }
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
    if (proxy != null) {
      proxy.onDestroy();
    }

    dispatchOnServiceDestroyed(this);

    super.onDestroy();
  }
}

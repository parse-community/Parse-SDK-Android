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
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.os.Bundle;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A utility class for retrieving app metadata such as the app name, default icon, whether or not
 * the app declares the correct permissions for push, etc.
 */
/** package */ class ManifestInfo {
  private static final String TAG = "com.parse.ManifestInfo";

  private static final Object lock = new Object();
  private static long lastModified = -1;
  /* package */ static int versionCode = -1;
  /* package */ static String versionName = null;
  private static int iconId = 0;
  private static String displayName = null;
  private static PushType pushType;

  /**
   * Returns the last time this application's APK was modified on disk. This is a proxy for both
   * version changes and if the APK has been restored from backup onto a different device.
   */
  public static long getLastModified() {
    synchronized (lock) {
      if (lastModified == -1) {
        File apkPath = new File(getContext().getApplicationInfo().sourceDir);
        lastModified = apkPath.lastModified();
      }
    }
    
    return lastModified;
  }
  
  /**
   * Returns the version code for this app, as specified by the android:versionCode attribute in the
   * <manifest> element of the manifest.
   */
  public static int getVersionCode() {
    synchronized (lock) {
      if (versionCode == -1) {
        try {
          versionCode = getPackageManager().getPackageInfo(getContext().getPackageName(), 0).versionCode;
        } catch (NameNotFoundException e) {
          PLog.e(TAG, "Couldn't find info about own package", e);
        }
      }
    }
    
    return versionCode;
  }

  /**
   * Returns the version name for this app, as specified by the android:versionName attribute in the
   * <manifest> element of the manifest.
   */
  public static String getVersionName() {
    synchronized (lock) {
      if (versionName == null) {
        try {
          versionName = getPackageManager().getPackageInfo(getContext().getPackageName(), 0).versionName;
        } catch (NameNotFoundException e) {
          PLog.e(TAG, "Couldn't find info about own package", e);
        }
      }
    }

    return versionName;
  }
  
  /**
   * Returns the display name of the app used by the app launcher, as specified by the android:label
   * attribute in the <application> element of the manifest.
   */
  public static String getDisplayName(Context context) {
    synchronized (lock) {
      if (displayName == null) {
        ApplicationInfo appInfo = context.getApplicationInfo();
        displayName = context.getPackageManager().getApplicationLabel(appInfo).toString();
      }
    }

    return displayName;
  }
  
  /**
   * Returns the default icon id used by this application, as specified by the android:icon
   * attribute in the <application> element of the manifest.
   */
  public static int getIconId() {
    synchronized (lock) {
      if (iconId == 0) {
        iconId = getContext().getApplicationInfo().icon;
      }
    }
    return iconId;
  }

  /**
   * Returns whether the given action has an associated receiver defined in the manifest.
   */
  /* package */ static boolean hasIntentReceiver(String action) {
    return !getIntentReceivers(action).isEmpty();
  }

  /**
   * Returns a list of ResolveInfo objects corresponding to the BroadcastReceivers with Intent Filters
   * specifying the given action within the app's package.
   */
  /* package */ static List<ResolveInfo> getIntentReceivers(String... actions) {
    Context context = getContext();
    PackageManager pm = context.getPackageManager();
    String packageName = context.getPackageName();
    List<ResolveInfo> list = new ArrayList<>();

    for (String action : actions) {
      list.addAll(pm.queryBroadcastReceivers(
              new Intent(action),
              PackageManager.GET_INTENT_FILTERS));
    }

    for (int i = list.size() - 1; i >= 0; --i) {
      String receiverPackageName = list.get(i).activityInfo.packageName;
      if (!receiverPackageName.equals(packageName)) {
        list.remove(i);
      }
    }
    return list;
  }

  // Should only be used for tests.
  static void setPushType(PushType newPushType) {
    synchronized (lock) {
      pushType = newPushType;
    }
  }
  
  /**
   * Inspects the app's manifest and returns whether the manifest contains required declarations to
   * be able to use GCM for push.
   */
  public static PushType getPushType() {
    synchronized (lock) {
      if (pushType == null) {
        pushType = findPushType();
        PLog.v(TAG, "Using " + pushType + " for push.");
      }
    }
    return pushType;
  }


  private static PushType findPushType() {
    if (!ParsePushBroadcastReceiver.isSupported()) {
      return PushType.NONE;
    }

    if (!PushServiceUtils.isSupported()) {
      return PushType.NONE;
    }

    // Ordered by preference.
    PushType[] types = PushType.types();
    for (PushType type : types) {
      PushHandler handler = PushHandler.Factory.create(type);
      PushHandler.SupportLevel level = handler.isSupported();
      String message = handler.getWarningMessage(level);
      switch (level) {
        case MISSING_REQUIRED_DECLARATIONS: // Can't use. notify.
          if (message != null) PLog.e(TAG, message);
          break;
        case MISSING_OPTIONAL_DECLARATIONS: // Using anyway.
          if (message != null) PLog.w(TAG, message);
          return type;
        case SUPPORTED:
          return type;
      }
    }
    return PushType.NONE;
  }

  /*
   * Returns a message that can be written to the system log if an app expects push to be enabled,
   * but push isn't actually enabled because the manifest is misconfigured.
   */
  static String getPushDisabledMessage() {
    return "Push is not configured for this app because the app manifest is missing required " +
        "declarations. To configure GCM, please add the following declarations to your app manifest: " +
        GcmPushHandler.getWarningMessage();
  }

  
  private static Context getContext() {
    return Parse.getApplicationContext();
  }

  private static PackageManager getPackageManager() {
    return getContext().getPackageManager();
  }

  private static ApplicationInfo getApplicationInfo(Context context, int flags) {
    try {
      return context.getPackageManager().getApplicationInfo(context.getPackageName(), flags);
    } catch (NameNotFoundException e) {
      return null;
    }
  }

  /**
   * @return A {@link Bundle} if meta-data is specified in AndroidManifest, otherwise null.
   */
  public static Bundle getApplicationMetadata(Context context) {
    ApplicationInfo info = getApplicationInfo(context, PackageManager.GET_META_DATA);
    if (info != null) {
      return info.metaData;
    }
    return null;
  }

  private static PackageInfo getPackageInfo(String name) {
    PackageInfo info = null;

    try {
      info = getPackageManager().getPackageInfo(name, 0);
    } catch (NameNotFoundException e) {
      // do nothing
    }

    return info;
  }

  static ServiceInfo getServiceInfo(Class<? extends Service> clazz) {
    ServiceInfo info = null;
    try {
      info = getPackageManager().getServiceInfo(new ComponentName(getContext(), clazz), 0);
    } catch (NameNotFoundException e) {
      // do nothing
    }

    return info;
  }

  private static ActivityInfo getReceiverInfo(Class<? extends BroadcastReceiver> clazz) {
    ActivityInfo info = null;
    try {
      info = getPackageManager().getReceiverInfo(new ComponentName(getContext(), clazz), 0);
    } catch (NameNotFoundException e) {
      // do nothing
    }
    
    return info;
  }

  /**
   * Returns {@code true} if this package has requested all of the listed permissions.
   * <p />
   * <strong>Note:</strong> This package might have requested all the permissions, but may not
   * be granted all of them.
   */
  static boolean hasRequestedPermissions(Context context, String... permissions) {
    String packageName = context.getPackageName();
    try {
      PackageInfo pi = context.getPackageManager().getPackageInfo(
          packageName, PackageManager.GET_PERMISSIONS);
      if (pi.requestedPermissions == null) {
        return false;
      }
      return Arrays.asList(pi.requestedPermissions).containsAll(Arrays.asList(permissions));
    } catch (NameNotFoundException e) {
      PLog.e(TAG, "Couldn't find info about own package", e);
      return false;
    }
  }

  /**
   * Returns {@code true} if this package has been granted all of the listed permissions.
   * <p />
   * <strong>Note:</strong> This package might have requested all the permissions, but may not
   * be granted all of them.
   */
  static boolean hasGrantedPermissions(Context context, String... permissions) {
    String packageName = context.getPackageName();
    PackageManager packageManager = context.getPackageManager();
    for (String permission : permissions) {
      if (packageManager.checkPermission(permission, packageName) != PackageManager.PERMISSION_GRANTED) {
        return false;
      }
    }
    
    return true;
  }

  private static boolean checkResolveInfo(Class<? extends BroadcastReceiver> clazz, List<ResolveInfo> infoList, String permission) {
    for (ResolveInfo info : infoList) {
      if (info.activityInfo != null) {
        final Class resolveInfoClass;
        try {
          resolveInfoClass = Class.forName(info.activityInfo.name);
        } catch (ClassNotFoundException e) {
          break;
        }
        if (clazz.isAssignableFrom(resolveInfoClass) && (permission == null || permission.equals(info.activityInfo.permission))) {
            return true;
        }
      }
    }

    return false;
  }

  static boolean checkReceiver(Class<? extends BroadcastReceiver> clazz, String permission, Intent[] intents) {
    for (Intent intent : intents) {
      List<ResolveInfo> receivers = getPackageManager().queryBroadcastReceivers(intent, 0);
      if (receivers.isEmpty()) {
        return false;
      }

      if (!checkResolveInfo(clazz, receivers, permission)) {
        return false;
      }
    }
    
    return true;
  }

  static boolean isGooglePlayServicesAvailable() {
    return getPackageInfo("com.google.android.gsf") != null;
  }
}

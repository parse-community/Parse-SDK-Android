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
    String packageName = context.getPackageName();
    List<ResolveInfo> list = new ArrayList<>();

    for (String action : actions) {
      list.addAll(
          context.getPackageManager().queryBroadcastReceivers(
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

  private static boolean usesPushBroadcastReceivers() {
    int intentsRegistered = 0;
    if (hasIntentReceiver(ParsePushBroadcastReceiver.ACTION_PUSH_RECEIVE)) {
      intentsRegistered++;
    }

    if (hasIntentReceiver(ParsePushBroadcastReceiver.ACTION_PUSH_OPEN)) {
      intentsRegistered++;
    }

    if (hasIntentReceiver(ParsePushBroadcastReceiver.ACTION_PUSH_DELETE)) {
      intentsRegistered++;
    }

    if (intentsRegistered != 0 && intentsRegistered != 3) {
      throw new SecurityException(
          "The Parse Push BroadcastReceiver must implement a filter for all of " +
          ParsePushBroadcastReceiver.ACTION_PUSH_RECEIVE + ", " +
          ParsePushBroadcastReceiver.ACTION_PUSH_OPEN + ", and " +
          ParsePushBroadcastReceiver.ACTION_PUSH_DELETE);
    }
    return intentsRegistered == 3;
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
        boolean isGooglePlayServicesAvailable = isGooglePlayServicesAvailable();
        boolean hasAnyGcmSpecificDeclaration = hasAnyGcmSpecificDeclaration();
        ManifestCheckResult gcmSupportLevel = gcmSupportLevel();

        boolean hasPushBroadcastReceiver = usesPushBroadcastReceivers();
        boolean hasRequiredGcmDeclarations = (gcmSupportLevel != ManifestCheckResult.MISSING_REQUIRED_DECLARATIONS);
        if (hasPushBroadcastReceiver && isGooglePlayServicesAvailable && hasRequiredGcmDeclarations) {
          pushType = PushType.GCM;
        } else {
          pushType = PushType.NONE;

          if (hasAnyGcmSpecificDeclaration) {
            if (!hasPushBroadcastReceiver) {
            /* Throw an error if someone migrated from an old SDK and hasn't yet started using
             * ParsePushBroadcastReceiver. */
              PLog.e(TAG, "Push is currently disabled. This is probably because you migrated " +
                  "from an older version of Parse. This version of Parse requires your app to " +
                  "have a BroadcastReceiver that handles " +
                  ParsePushBroadcastReceiver.ACTION_PUSH_RECEIVE + ", " +
                  ParsePushBroadcastReceiver.ACTION_PUSH_OPEN + ", and " +
                  ParsePushBroadcastReceiver.ACTION_PUSH_DELETE + ". You can do this by adding " +
                  "these lines to your AndroidManifest.xml:\n\n" +
                  " <receiver android:name=\"com.parse.ParsePushBroadcastReceiver\"\n" +
                  "   android:exported=false>\n" +
                  "  <intent-filter>\n" +
                  "     <action android:name=\"com.parse.push.intent.RECEIVE\" />\n" +
                  "     <action android:name=\"com.parse.push.intent.OPEN\" />\n" +
                  "     <action android:name=\"com.parse.push.intent.DELETE\" />\n" +
                  "   </intent-filter>\n" +
                  " </receiver>");
            }

            if (!isGooglePlayServicesAvailable) {
              PLog.e(TAG, "Cannot use GCM for push on this device because Google Play " +
                  "Services is not available. Install Google Play Services from the Play Store.");
            }

            // Emit warnings if the client doesn't get push due to misconfiguration of the manifest.
            if (!hasRequiredGcmDeclarations) {
            /*
             * If we detect that the app has some GCM-specific declarations, but not all required
             * declarations for GCM, then most likely the client means to use GCM but misconfigured
             * their manifest. Log an error in this case.
             */
              PLog.e(TAG, "Cannot use GCM for push because the app manifest is missing some " +
                  "required declarations. Please " + getGcmManifestMessage());
            }
          }
        }

        PLog.v(TAG, "Using " + pushType + " for push.");

        /*
         * If we selected gcm but the manifest is missing some optional declarations, warn so
         * the user knows how to add those optional declarations.
         */
        if (Parse.getLogLevel() <= Parse.LOG_LEVEL_WARNING) {
          if (pushType == PushType.GCM && gcmSupportLevel == ManifestCheckResult.MISSING_OPTIONAL_DECLARATIONS) {
            PLog.w(TAG, "Using GCM for Parse Push, but the app manifest is missing some optional " +
                "declarations that should be added for maximum reliability. Please " +
                getGcmManifestMessage());
          }
        }
      }
    }
    
    return pushType;
  }

  /*
   * Returns a message that can be written to the system log if an app expects push to be enabled,
   * but push isn't actually enabled because the manifest is misconfigured.
   */
  static String getNonePushTypeLogMessage() {
    return "Push is not configured for this app because the app manifest is missing required " +
        "declarations. Please add the following declarations to your app manifest to use GCM for " +
        "push: " + getGcmManifestMessage();
  }

  private enum ManifestCheckResult {
    /* 
     * Manifest has all required and optional declarations necessary to support this push service.
     */
    HAS_ALL_DECLARATIONS,

    /*
     * Manifest has all required declarations to support this push service, but is missing some
     * optional declarations.
     */
    MISSING_OPTIONAL_DECLARATIONS,

    /*
     * Manifest doesn't have enough required declarations to support this push service.
     */
    MISSING_REQUIRED_DECLARATIONS
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

  private static ServiceInfo getServiceInfo(Class<? extends Service> clazz) {
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
  private static boolean hasRequestedPermissions(Context context, String... permissions) {
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
  private static boolean hasGrantedPermissions(Context context, String... permissions) {
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

  private static boolean checkReceiver(Class<? extends BroadcastReceiver> clazz, String permission, Intent[] intents) {
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

  private static boolean hasAnyGcmSpecificDeclaration() {
    Context context = getContext();
    if (hasRequestedPermissions(context, "com.google.android.c2dm.permission.RECEIVE") ||
        hasRequestedPermissions(context, context.getPackageName() + ".permission.C2D_MESSAGE") ||
        getReceiverInfo(GcmBroadcastReceiver.class) != null) {
      return true;
    }

    return false;
  }

  private static boolean isGooglePlayServicesAvailable() {
    return getPackageInfo("com.google.android.gsf") != null;
  }

  private static ManifestCheckResult gcmSupportLevel() {
    Context context = getContext();
    if (getServiceInfo(PushService.class) == null) {
      return ManifestCheckResult.MISSING_REQUIRED_DECLARATIONS;
    }

    String[] requiredPermissions = new String[] {
      "android.permission.INTERNET",
      "android.permission.ACCESS_NETWORK_STATE",
      "android.permission.WAKE_LOCK",
      "com.google.android.c2dm.permission.RECEIVE",
      context.getPackageName() + ".permission.C2D_MESSAGE"
    };
    
    if (!hasRequestedPermissions(context, requiredPermissions)) {
      return ManifestCheckResult.MISSING_REQUIRED_DECLARATIONS;
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
    
    if (!checkReceiver(GcmBroadcastReceiver.class, rcvrPermission, intents)) {
      return ManifestCheckResult.MISSING_REQUIRED_DECLARATIONS;
    }

    String[] optionalPermissions = new String[] {
      "android.permission.VIBRATE"
    };

    if (!hasGrantedPermissions(context, optionalPermissions)) {
      return ManifestCheckResult.MISSING_OPTIONAL_DECLARATIONS;
    }
    
    return ManifestCheckResult.HAS_ALL_DECLARATIONS;
  }
  
  private static String getGcmManifestMessage() {
    String packageName = getContext().getPackageName();
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
}

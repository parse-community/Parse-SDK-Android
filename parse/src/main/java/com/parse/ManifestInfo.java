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
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.os.Bundle;

import java.util.ArrayList;
import java.util.List;

/**
 * A utility class for retrieving app metadata such as the app name, default icon, whether or not
 * the app declares the correct permissions for push, etc.
 */
public class ManifestInfo {
    private static final String TAG = "com.parse.ManifestInfo";

    private static final Object lock = new Object();
    /* package */ static int versionCode = -1;
    /* package */ static String versionName = null;
    private static int iconId = 0;
    private static String displayName = null;

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
                    versionName = "unknown";
                }
                if (versionName == null) {
                    // Some contexts, such as instrumentation tests can always have this value
                    // return as null. We will change to "unknown" for this case as well, so that
                    // an exception isn't thrown for adding a null header later.
                    versionName = "unknown";
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
     * Returns a list of ResolveInfo objects corresponding to the BroadcastReceivers with Intent Filters
     * specifying the given action within the app's package.
     */
    /* package */
    static List<ResolveInfo> getIntentReceivers(String... actions) {
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
}

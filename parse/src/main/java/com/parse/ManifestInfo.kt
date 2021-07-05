/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Bundle
import com.parse.PLog.e
import java.util.*

/**
 * A utility class for retrieving app metadata such as the app name, default icon, whether or not
 * the app declares the correct permissions for push, etc.
 */
object ManifestInfo {
    private const val TAG = "com.parse.ManifestInfo"
    private val lock = Any()

    /**
     * Returns the version code for this app, as specified by the android:versionCode attribute in the
     * <manifest> element of the manifest.
    </manifest> */
    var versionCode = -1
        get() {
            synchronized(lock) {
                if (versionCode == -1) {
                    try {
                        return packageManager.getPackageInfo(context.packageName, 0).versionCode
                    } catch (e: PackageManager.NameNotFoundException) {
                        e(TAG, "Couldn't find info about own package", e)
                    }
                }
            }
            return field
        }

    /**
     * Returns the version name for this app, as specified by the android:versionName attribute in the
     * <manifest> element of the manifest.
    </manifest> */
    var versionName: String? = null
        get() {
            synchronized(lock) {
                if (versionName == null) {
                    versionName = try {
                        packageManager.getPackageInfo(context.packageName, 0).versionName
                    } catch (e: PackageManager.NameNotFoundException) {
                        e(TAG, "Couldn't find info about own package", e)
                        "unknown"
                    }
                    if (versionName == null) {
                        // Some contexts, such as instrumentation tests can always have this value
                        // return as null. We will change to "unknown" for this case as well, so that
                        // an exception isn't thrown for adding a null header later.
                        versionName = "unknown"
                    }
                }
            }
            return field
        }

    /**
     * Returns the default icon id used by this application, as specified by the android:icon
     * attribute in the <application> element of the manifest.
    </application> */
    @JvmStatic
    var iconId = 0
        get() {
            synchronized(lock) {
                if (field == 0) {
                    field = context.applicationInfo.icon
                }
            }
            return field
        }
        private set

    /**
     * Returns the display name of the app used by the app launcher, as specified by the android:label
     * attribute in the <application> element of the manifest.
    </application> */
    private val displayName: String?
        get() {
            synchronized(lock) {
                if (displayName == null) {
                    val appInfo = context.applicationInfo
                    return context.packageManager.getApplicationLabel(appInfo).toString()
                }
            }
            return displayName
        }


    /**
     * Returns a list of ResolveInfo objects corresponding to the BroadcastReceivers with Intent Filters
     * specifying the given action within the app's package.
     */
    /* package */
    @JvmStatic
    fun getIntentReceivers(vararg actions: String?): List<ResolveInfo> {
        val context = context
        val pm = context.packageManager
        val packageName = context.packageName
        val list: MutableList<ResolveInfo> = ArrayList()
        for (action in actions) {
            list.addAll(
                pm.queryBroadcastReceivers(
                    Intent(action),
                    PackageManager.GET_INTENT_FILTERS
                )
            )
        }
        for (i in list.indices.reversed()) {
            val receiverPackageName = list[i].activityInfo.packageName
            if (receiverPackageName != packageName) {
                list.removeAt(i)
            }
        }
        return list
    }

    private val context: Context
        get() = Parse.getApplicationContext()
    private val packageManager: PackageManager
        get() = context.packageManager

    private fun getApplicationInfo(context: Context, flags: Int): ApplicationInfo? {
        return try {
            context.packageManager.getApplicationInfo(context.packageName, flags)
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    /**
     * @return A [Bundle] if meta-data is specified in AndroidManifest, otherwise null.
     */
    @JvmStatic
    fun getApplicationMetadata(context: Context): Bundle? {
        val info = getApplicationInfo(context, PackageManager.GET_META_DATA)
        return info?.metaData
    }
}
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
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.util.SparseArray;

/** package */ final class ServiceUtils {
  private static final String TAG = "com.parse.ServiceUtils";
  private static final String WAKE_LOCK_EXTRA = "parseWakeLockId";

  private static final SparseArray<ParseWakeLock> wakeLocks = new SparseArray<>();
  private static int wakeLockId = 0;

  /*
   * Same as Context.startService, but logs an error if starting the service fails.
   */
  public static boolean runIntentInService(
      Context context, Intent intent, Class<? extends Service> clazz) {
    boolean startedService = false;
    
    if (intent != null) {
      if (clazz != null) {
        intent.setClass(context, clazz);
      }
      
      ComponentName name = context.startService(intent);
      
      startedService = (name != null);
      if (!startedService) {
        PLog.e(TAG, "Could not start the service. Make sure that the XML tag "
            + "<service android:name=\"" + clazz + "\" /> is in your "
            + "AndroidManifest.xml as a child of the <application> element.");
      }
    }
    
    return startedService;
  }

  /*
   * Same as Context.startService, but acquires a wake lock before starting the service. The wake
   * lock must later be released by calling completeWakefulIntent().
   */
  public static boolean runWakefulIntentInService(
      Context context, Intent intent, Class<? extends Service> clazz) {
    boolean startedService = false;
    
    if (intent != null) {
      String reason = intent.toString();
      ParseWakeLock wl = ParseWakeLock.acquireNewWakeLock(context, PowerManager.PARTIAL_WAKE_LOCK, reason, 0);      
      
      synchronized (wakeLocks) {
        intent.putExtra(WAKE_LOCK_EXTRA, wakeLockId);
        wakeLocks.append(wakeLockId, wl);
        wakeLockId++;
      }
      
      startedService = runIntentInService(context, intent, clazz);
      if (!startedService) {
        completeWakefulIntent(intent);
      }
    }
    
    return startedService;
  }
  
  /*
   * Releases the wake lock acquired by runWakefulIntentInService for this intent.
   */
  public static void completeWakefulIntent(Intent intent) {
    if (intent != null && intent.hasExtra(WAKE_LOCK_EXTRA)) {
      int id = intent.getIntExtra(WAKE_LOCK_EXTRA, -1);
      ParseWakeLock wakeLock;
      
      synchronized (wakeLocks) {
        wakeLock = wakeLocks.get(id);
        wakeLocks.remove(id);
      }
      
      if (wakeLock == null) {
        PLog.e(TAG, "Got wake lock id of " + id + " in intent, but no such lock found in " +
            "global map. Was completeWakefulIntent called twice for the same intent?");
      } else {
        wakeLock.release();
      }
    }
  }
}

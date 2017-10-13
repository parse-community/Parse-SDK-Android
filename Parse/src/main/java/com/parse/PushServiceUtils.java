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
import android.os.Build;
import android.support.annotation.NonNull;

import bolts.Task;


/**
 * Helper class mostly used to access and wake the push dispatching class, plus some other utilities.
 *
 * Android O introduces limitations over Context.startService. If the app is currently considered
 * in background, the call will result in a crash. The only reliable solutions are either using
 * Context.startServiceInForeground, which does not fit our case, or move to the JobScheduler
 * engine, which is what we do here for Oreo, launching {@link PushServiceApi26}.
 *
 * Pre-oreo, we just launch {@link PushService}.
 *
 * See:
 * https://developer.android.com/about/versions/oreo/background.html
 * https://developer.android.com/reference/android/support/v4/content/WakefulBroadcastReceiver.html
 */
abstract class PushServiceUtils {
  private static final boolean USE_JOBS = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O;

  /**
   * Wakes the PushService class by running it either as a Service or as a scheduled job
   * depending on API level.
   *
   * @param context calling context
   * @param intent non-null intent to be passed to the PushHandlers
   * @return true if service could be launched
   */
  public static boolean runService(Context context, @NonNull Intent intent) {
    if (USE_JOBS) {
      return PushServiceApi26.run(context, intent);
    } else {
      return PushService.run(context, intent);
    }
  }

  // Checks the manifest file.
  static boolean isSupported() {
    if (USE_JOBS) {
      return PushServiceApi26.isSupported();
    } else {
      return PushService.isSupported();
    }
  }

  // Some handlers might need initialization.
  static Task<Void> initialize() {
    return createPushHandler().initialize();
  }

  static PushHandler createPushHandler() {
    return PushHandler.Factory.create(ManifestInfo.getPushType());
  }
}

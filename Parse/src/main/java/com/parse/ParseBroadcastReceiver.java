/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * @exclude
 */
public class ParseBroadcastReceiver extends BroadcastReceiver {
  private static final String TAG = "com.parse.ParseBroadcastReceiver";

  @Override
  public void onReceive(Context context, Intent intent) {
    // This exists to restart the service when the device is first turned
    // on, or on other system events for which we want to ensure that the
    // push service is running.
    PLog.d(TAG, "received " + intent.getAction());
    PushService.startServiceIfRequired(context);
  }
}

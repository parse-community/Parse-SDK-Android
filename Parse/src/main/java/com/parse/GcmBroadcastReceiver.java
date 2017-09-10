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
import android.support.annotation.CallSuper;

/**
 * @exclude
 */
public class GcmBroadcastReceiver extends BroadcastReceiver {
  @Override
  @CallSuper
  public void onReceive(Context context, Intent intent) {
    PushServiceUtils.runService(context, intent);
  }
}

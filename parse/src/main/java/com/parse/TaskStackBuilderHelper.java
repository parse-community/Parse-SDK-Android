/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.TaskStackBuilder;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/**
 * This is here to avoid the dependency on the android support library.
 * TaskStackBuilder was introduced in API 11, so in order to eliminate warnings of the type
 * 'Could not find class...' this takes advantage of lazy class loading.
 * TODO (pdjones): make more similar to support-v4 api
 */
@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
/* package */ class TaskStackBuilderHelper {
  public static void startActivities(Context context, Class<? extends Activity> cls, Intent activityIntent) {
    TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
    stackBuilder.addParentStack(cls);
    stackBuilder.addNextIntent(activityIntent);
    stackBuilder.startActivities();
  }
}

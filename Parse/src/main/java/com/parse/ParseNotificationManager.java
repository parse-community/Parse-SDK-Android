/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import java.util.concurrent.atomic.AtomicInteger;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.content.res.Resources.NotFoundException;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.util.SparseIntArray;

/**
 * A utility class for building and showing notifications.
 */
/** package */ class ParseNotificationManager {
  public static final String TAG = "com.parse.ParseNotificationManager";
  
  public static class Singleton {
    private static final ParseNotificationManager INSTANCE = new ParseNotificationManager();
  }
  
  public static ParseNotificationManager getInstance() {
    return Singleton.INSTANCE;
  }
  
  private final AtomicInteger notificationCount = new AtomicInteger(0);
  private volatile boolean shouldShowNotifications = true;
  
  public void setShouldShowNotifications(boolean show) {
    shouldShowNotifications = show;
  }
  
  public int getNotificationCount() {
    return notificationCount.get();
  }
  
  public void showNotification(Context context, Notification notification) {
    if (context != null && notification != null) {
      notificationCount.incrementAndGet();
      
      if (shouldShowNotifications) {
        // Fire off the notification
        NotificationManager nm =
            (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        
        // Pick an id that probably won't overlap anything
        int notificationId = (int)System.currentTimeMillis();

        try {
          nm.notify(notificationId, notification);
        } catch (SecurityException e) {
          // Some phones throw an exception for unapproved vibration
          notification.defaults = Notification.DEFAULT_LIGHTS | Notification.DEFAULT_SOUND;
          nm.notify(notificationId, notification);
        }
      }
    }
  }
}

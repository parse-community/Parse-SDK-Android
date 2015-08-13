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
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.content.res.Resources.NotFoundException;
import android.graphics.drawable.Drawable;
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
  
  private final Object lock = new Object();
  private final AtomicInteger notificationCount = new AtomicInteger(0);
  private volatile boolean shouldShowNotifications = true;
  
  // protected by object lock
  private SparseIntArray iconIds = new SparseIntArray();

  public boolean getShouldShowNotifications() {
    return shouldShowNotifications;
  }
  
  public void setShouldShowNotifications(boolean show) {
    shouldShowNotifications = show;
  }
  
  public int getNotificationCount() {
    return notificationCount.get();
  }
  
  /*
   * Notifications must be created with a valid drawable iconId resource, or NotificationManager
   * will silently discard the notification. To help our clients, we check on the validity of the
   * iconId before creating the Notification and log an error when provided an invalid id.
   */ 
  public boolean isValidIconId(Context context, int iconId) {
    int valid;
    
    synchronized (lock) {
      valid = iconIds.get(iconId, -1);
    }
    
    if (valid == -1) {
      Resources resources = context.getResources();
      Drawable drawable = null;
      
      try {
        drawable = resources.getDrawable(iconId);
      } catch (NotFoundException e) {
        // do nothing
      }
      
      synchronized (lock) {
        valid = (drawable == null) ? 0 : 1;
        iconIds.put(iconId, valid);
      }
    }
    
    return valid == 1;
  }
  
  public Notification createNotification(Context context, String title, String body,
      Class<? extends Activity> clazz, int iconId, Bundle extras) {
    Notification notification = null;
    
    if (!isValidIconId(context, iconId)) {
      PLog.e(TAG, "Icon id " + iconId + " is not a valid drawable. Trying to fall back to " +
          "default app icon.");
      iconId = ManifestInfo.getIconId();
    }
    
    if (iconId == 0) {
      PLog.e(TAG, "Could not find a valid icon id for this app. This is required to create a " +
          "Notification object to show in the status bar. Make sure that the <application> in " +
          "in your Manifest.xml has a valid android:icon attribute.");
    } else if (context == null || title == null || body == null || clazz == null || iconId == 0) {
      PLog.e(TAG, "Must specify non-null context, title, body, and activity class to show " +
          "notification.");
    } else {
      long when = System.currentTimeMillis();
      ComponentName name = new ComponentName(context, clazz);
      
      Intent intent = new Intent();
      intent.setComponent(name);
      intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      
      if (extras != null) {
        intent.putExtras(extras);
      }
      
      PendingIntent contentIntent = PendingIntent.getActivity(context, (int)when, intent, 0);

      // Construct a Notification
      notification = new Notification(iconId, body, when);
      notification.flags |= Notification.FLAG_AUTO_CANCEL;
      notification.defaults |= Notification.DEFAULT_ALL;
      notification.setLatestEventInfo(context, title, body, contentIntent);
    }
    
    return notification;
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
  
  public void showNotification(Context context, String title, String body,
      Class<? extends Activity> cls, int iconId, Bundle extras) {
    showNotification(context, createNotification(context, title, body, cls, iconId, extras));
  }
}

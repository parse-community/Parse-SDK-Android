/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * A utility class for building and showing notifications.
 */
class ParseNotificationManager {
    private final AtomicInteger notificationCount = new AtomicInteger(0);

    public static ParseNotificationManager getInstance() {
        return Singleton.INSTANCE;
    }

    public void showNotification(Context context, Notification notification) {
        if (context != null && notification != null) {
            notificationCount.incrementAndGet();

            // Fire off the notification
            NotificationManager nm =
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

            // Pick an id that probably won't overlap anything
            int notificationId = (int) System.currentTimeMillis();

            try {
                nm.notify(notificationId, notification);
            } catch (SecurityException e) {
                // Some phones throw an exception for unapproved vibration
                notification.defaults = Notification.DEFAULT_LIGHTS | Notification.DEFAULT_SOUND;
                nm.notify(notificationId, notification);
            }
        }
    }

    private static class Singleton {
        private static final ParseNotificationManager INSTANCE = new ParseNotificationManager();
    }
}

/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.parse;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Build;
import android.widget.RemoteViews;

/**
 * A simple implementation of the NotificationCompat class from android-support-v4
 * It only differentiates between devices before and after JellyBean because the only extra
 * feature that we currently support between the two device types is BigTextStyle notifications.
 * This class takes advantage of lazy class loading to eliminate warnings of the type
 * 'Could not find class...'
 */
/** package */ class NotificationCompat {
  /**
   * Obsolete flag indicating high-priority notifications; use the priority field instead.
   *
   * @deprecated Use {@link NotificationCompat.Builder#setPriority(int)} with a positive value.
   */
  public static final int FLAG_HIGH_PRIORITY = 0x00000080;

  /**
   * Default notification priority for {@link NotificationCompat.Builder#setPriority(int)}.
   * If your application does not prioritize its own notifications,
   * use this value for all notifications.
   */
  public static final int PRIORITY_DEFAULT = 0;

  private static final NotificationCompatImpl IMPL;

  interface NotificationCompatImpl {
    Notification build(Builder b);
  }

  static class NotificationCompatImplBase implements  NotificationCompatImpl {
    @Override
    public Notification build(Builder builder) {
      Notification result = builder.mNotification;
      NotificationCompat.Builder newBuilder = new NotificationCompat.Builder(builder.mContext);
      newBuilder.setContentTitle(builder.mContentTitle);
      newBuilder.setContentText(builder.mContentText);
      newBuilder.setContentIntent(builder.mContentIntent);
      // translate high priority requests into legacy flag
      if (builder.mPriority > PRIORITY_DEFAULT) {
        result.flags |= FLAG_HIGH_PRIORITY;
      }
      return newBuilder.build();
    }
  }

  @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
  static class NotificationCompatPostJellyBean implements NotificationCompatImpl {
    private Notification.Builder postJellyBeanBuilder;
    @Override
    public Notification build(Builder b) {
      postJellyBeanBuilder = new Notification.Builder(b.mContext);
      postJellyBeanBuilder.setContentTitle(b.mContentTitle)
          .setContentText(b.mContentText)
          .setTicker(b.mNotification.tickerText)
          .setSmallIcon(b.mNotification.icon, b.mNotification.iconLevel)
          .setContentIntent(b.mContentIntent)
          .setDeleteIntent(b.mNotification.deleteIntent)
          .setAutoCancel((b.mNotification.flags & Notification.FLAG_AUTO_CANCEL) != 0)
          .setLargeIcon(b.mLargeIcon)
          .setDefaults(b.mNotification.defaults);
      if (b.mStyle != null) {
        if (b.mStyle instanceof Builder.BigTextStyle) {
          Builder.BigTextStyle staticStyle = (Builder.BigTextStyle) b.mStyle;
          Notification.BigTextStyle style = new Notification.BigTextStyle(postJellyBeanBuilder)
              .setBigContentTitle(staticStyle.mBigContentTitle)
              .bigText(staticStyle.mBigText);
          if (staticStyle.mSummaryTextSet) {
            style.setSummaryText(staticStyle.mSummaryText);
          }
        }
      }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        postJellyBeanBuilder.setChannelId(b.mNotificationChannelId);
      }
      return postJellyBeanBuilder.build();
    }
  }

  static {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
      IMPL = new NotificationCompatPostJellyBean();
    } else {
      IMPL = new NotificationCompatImplBase();
    }
  }

  public static class Builder {
    /**
     * Maximum length of CharSequences accepted by Builder and friends.
     *
     * <p>
     * Avoids spamming the system with overly large strings such as full e-mails.
     */
    private static final int MAX_CHARSEQUENCE_LENGTH = 5 * 1024;

    Context mContext;

    CharSequence mContentTitle;
    CharSequence mContentText;
    PendingIntent mContentIntent;
    Bitmap mLargeIcon;
    int mPriority;
    Style mStyle;
    String mNotificationChannelId;

    Notification mNotification = new Notification();

    /**
     * Constructor.
     *
     * Automatically sets the when field to {@link System#currentTimeMillis()
     * System.currentTimeMillis()} and the audio stream to the
     * {@link Notification#STREAM_DEFAULT}.
     *
     * @param context A {@link Context} that will be used to construct the
     *      RemoteViews. The Context will not be held past the lifetime of this
     *      Builder object.
     */
    public Builder(Context context) {
      mContext = context;

      // Set defaults to match the defaults of a Notification
      mNotification.when = System.currentTimeMillis();
      mNotification.audioStreamType = Notification.STREAM_DEFAULT;
      mPriority = PRIORITY_DEFAULT;
    }

    public Builder setWhen(long when) {
      mNotification.when = when;
      return this;
    }

    /**
     * Set the small icon to use in the notification layouts.  Different classes of devices
     * may return different sizes.  See the UX guidelines for more information on how to
     * design these icons.
     *
     * @param icon A resource ID in the application's package of the drawble to use.
     */
    public Builder setSmallIcon(int icon) {
      mNotification.icon = icon;
      return this;
    }

    /**
     * A variant of {@link #setSmallIcon(int) setSmallIcon(int)} that takes an additional
     * level parameter for when the icon is a {@link android.graphics.drawable.LevelListDrawable
     * LevelListDrawable}.
     *
     * @param icon A resource ID in the application's package of the drawble to use.
     * @param level The level to use for the icon.
     *
     * @see android.graphics.drawable.LevelListDrawable
     */
    public Builder setSmallIcon(int icon, int level) {
      mNotification.icon = icon;
      mNotification.iconLevel = level;
      return this;
    }

    /**
     * Set the title (first row) of the notification, in a standard notification.
     */
    public Builder setContentTitle(CharSequence title) {
      mContentTitle = limitCharSequenceLength(title);
      return this;
    }

    /**
     * Set the notification channel of the notification, in a standard notification.
     */
    public Builder setNotificationChannel(String notificationChannelId) {
      mNotificationChannelId = notificationChannelId;
      return this;
    }

    /**
     * Set the text (second row) of the notification, in a standard notification.
     */
    public Builder setContentText(CharSequence text) {
      mContentText = limitCharSequenceLength(text);
      return this;
    }

    /**
     * Supply a {@link PendingIntent} to send when the notification is clicked.
     * If you do not supply an intent, you can now add PendingIntents to individual
     * views to be launched when clicked by calling {@link RemoteViews#setOnClickPendingIntent
     * RemoteViews.setOnClickPendingIntent(int,PendingIntent)}.  Be sure to
     * read {@link Notification#contentIntent Notification.contentIntent} for
     * how to correctly use this.
     */
    public Builder setContentIntent(PendingIntent intent) {
      mContentIntent = intent;
      return this;
    }

    /**
     * Supply a {@link PendingIntent} to send when the notification is cleared by the user
     * directly from the notification panel.  For example, this intent is sent when the user
     * clicks the "Clear all" button, or the individual "X" buttons on notifications.  This
     * intent is not sent when the application calls {@link NotificationManager#cancel
     * NotificationManager.cancel(int)}.
     */
    public Builder setDeleteIntent(PendingIntent intent) {
      mNotification.deleteIntent = intent;
      return this;
    }

    /**
     * Set the text that is displayed in the status bar when the notification first
     * arrives.
     */
    public Builder setTicker(CharSequence tickerText) {
      mNotification.tickerText = limitCharSequenceLength(tickerText);
      return this;
    }

    /**
     * Set the large icon that is shown in the ticker and notification.
     */
    public Builder setLargeIcon(Bitmap icon) {
      mLargeIcon = icon;
      return this;
    }

    /**
     * Setting this flag will make it so the notification is automatically
     * canceled when the user clicks it in the panel.  The PendingIntent
     * set with {@link #setDeleteIntent} will be broadcast when the notification
     * is canceled.
     */
    public Builder setAutoCancel(boolean autoCancel) {
      setFlag(Notification.FLAG_AUTO_CANCEL, autoCancel);
      return this;
    }

    /**
     * Set the default notification options that will be used.
     * <p>
     * The value should be one or more of the following fields combined with
     * bitwise-or:
     * {@link Notification#DEFAULT_SOUND}, {@link Notification#DEFAULT_VIBRATE},
     * {@link Notification#DEFAULT_LIGHTS}.
     * <p>
     * For all default values, use {@link Notification#DEFAULT_ALL}.
     */
    public Builder setDefaults(int defaults) {
      mNotification.defaults = defaults;
      if ((defaults & Notification.DEFAULT_LIGHTS) != 0) {
        mNotification.flags |= Notification.FLAG_SHOW_LIGHTS;
      }
      return this;
    }

    private void setFlag(int mask, boolean value) {
      if (value) {
        mNotification.flags |= mask;
      } else {
        mNotification.flags &= ~mask;
      }
    }

    /**
     * Set the relative priority for this notification.
     *
     * Priority is an indication of how much of the user's
     * valuable attention should be consumed by this
     * notification. Low-priority notifications may be hidden from
     * the user in certain situations, while the user might be
     * interrupted for a higher-priority notification.
     * The system sets a notification's priority based on various factors including the
     * setPriority value. The effect may differ slightly on different platforms.
     */
    public Builder setPriority(int pri) {
      mPriority = pri;
      return this;
    }

    /**
     * Add a rich notification style to be applied at build time.
     * <br>
     * If the platform does not provide rich notification styles, this method has no effect. The
     * user will always see the normal notification style.
     *
     * @param style Object responsible for modifying the notification style.
     */
    public Builder setStyle(Style style) {
      if (mStyle != style) {
        mStyle = style;
        if (mStyle != null) {
          mStyle.setBuilder(this);
        }
      }
      return this;
    }

    /**
     * @deprecated Use {@link #build()} instead.
     */
    @Deprecated
    public Notification getNotification() {
      return IMPL.build(this);
    }

    /**
     * Combine all of the options that have been set and return a new {@link Notification}
     * object.
     */
    public Notification build() {
      return IMPL.build(this);
    }

    protected static CharSequence limitCharSequenceLength(CharSequence cs) {
      if (cs == null) return cs;
      if (cs.length() > MAX_CHARSEQUENCE_LENGTH) {
        cs = cs.subSequence(0, MAX_CHARSEQUENCE_LENGTH);
      }
      return cs;
    }

    /**
     * An object that can apply a rich notification style to a {@link Notification.Builder}
     * object.
     * <br>
     * If the platform does not provide rich notification styles, methods in this class have no
     * effect.
     */
    public static abstract class Style
    {
      Builder mBuilder;
      CharSequence mBigContentTitle;
      CharSequence mSummaryText;
      boolean mSummaryTextSet = false;

      public void setBuilder(Builder builder) {
        if (mBuilder != builder) {
          mBuilder = builder;
          if (mBuilder != null) {
            mBuilder.setStyle(this);
          }
        }
      }

      public Notification build() {
        Notification notification = null;
        if (mBuilder != null) {
          notification = mBuilder.build();
        }
        return notification;
      }
    }

    /**
     * Helper class for generating large-format notifications that include a lot of text.
     *
     * <br>
     * If the platform does not provide large-format notifications, this method has no effect. The
     * user will always see the normal notification view.
     * <br>
     * This class is a "rebuilder": It attaches to a Builder object and modifies its behavior, like so:
     * <pre class="prettyprint">
     * Notification noti = new Notification.Builder()
     *     .setContentTitle(&quot;New mail from &quot; + sender.toString())
     *     .setContentText(subject)
     *     .setSmallIcon(R.drawable.new_mail)
     *     .setLargeIcon(aBitmap)
     *     .setStyle(new Notification.BigTextStyle()
     *         .bigText(aVeryLongString))
     *     .build();
     * </pre>
     *
     * @see Notification#bigContentView
     */
    public static class BigTextStyle extends Style {
      CharSequence mBigText;

      public BigTextStyle() {
      }

      public BigTextStyle(Builder builder) {
        setBuilder(builder);
      }

      /**
       * Overrides ContentTitle in the big form of the template.
       * This defaults to the value passed to setContentTitle().
       */
      public BigTextStyle setBigContentTitle(CharSequence title) {
        mBigContentTitle = title;
        return this;
      }

      /**
       * Set the first line of text after the detail section in the big form of the template.
       */
      public BigTextStyle setSummaryText(CharSequence cs) {
        mSummaryText = cs;
        mSummaryTextSet = true;
        return this;
      }

      /**
       * Provide the longer text to be displayed in the big form of the
       * template in place of the content text.
       */
      public BigTextStyle bigText(CharSequence cs) {
        mBigText = cs;
        return this;
      }
    }
  }
}

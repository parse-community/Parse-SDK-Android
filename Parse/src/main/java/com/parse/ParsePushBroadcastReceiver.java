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
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * A {@link BroadcastReceiver} for rendering and reacting to to Notifications.
 * <p/>
 * This {@link BroadcastReceiver} must be registered in order to use the {@link ParsePush}
 * subscription methods. As a security precaution, the intent filters for this
 * {@link BroadcastReceiver} must not be exported. Add the following lines to your
 * {@code AndroidManifest.xml} file, inside the &lt;application&gt; element to properly register the
 * {@code ParsePushBroadcastReceiver}:
 * <p/>
 * <pre>
 * &lt;receiver android:name="com.parse.ParsePushBroadcastReceiver" android:exported=false&gt;
 *  &lt;intent-filter&gt;
 *     &lt;action android:name="com.parse.push.intent.RECEIVE" /&gt;
 *     &lt;action android:name="com.parse.push.intent.OPEN" /&gt;
 *     &lt;action android:name="com.parse.push.intent.DELETE" /&gt;
 *   &lt;/intent-filter&gt;
 * &lt;/receiver&gt;
 * </pre>
 * <p/>
 * The {@code ParsePushBroadcastReceiver} is designed to provide maximal configurability with
 * minimal effort. To customize the push icon, add the following line as a child of your
 * &lt;application&gt; element:
 * <p/>
 * <pre>
 *   &lt;meta-data android:name=&quot;com.parse.push.notification_icon&quot;
 *              android:resource=&quot;@drawable/icon&quot;/&gt;
 * </pre>
 * <p/>
 * where {@code drawable/icon} may be the path to any drawable resource. The
 * <a href="http://developer.android.com/design/style/iconography.html#notification">Android style
 * guide</a> for Notifications suggests that push icons should be flat monochromatic images.
 * <p/>
 * To achieve further customization, {@code ParsePushBroadcastReceiver} can be subclassed. When
 * providing your own implementation of {@code ParsePushBroadcastReceiver}, be sure to change
 * {@code com.parse.PushBroadcastReceiver} to the name of your custom subclass in your
 * AndroidManifest.xml. You can intercept and override the behavior of entire portions of the
 * push lifecycle by overriding {@link #onPushReceive(Context, Intent)},
 * {@link #onPushOpen(Context, Intent)}, or {@link #onPushDismiss(Context, Intent)}.
 * To make minor changes to the appearance of a notification, override
 * {@link #getSmallIconId(Context, Intent)} or {@link #getLargeIcon(Context, Intent)}. To completely
 * change the Notification generated, override {@link #getNotification(Context, Intent)}. To
 * change the NotificationChannel generated, override {@link #getNotificationChannel(Context, Intent)}. To
 * change how the NotificationChannel is created, override {@link #createNotificationChannel(Context, NotificationChannel)}.
 * To change the Activity launched when a user opens a Notification, override
 * {@link #getActivity(Context, Intent)}.
 */
// Hack note: Javadoc smashes the last two paragraphs together without the <p> tags.
public class ParsePushBroadcastReceiver extends BroadcastReceiver {
  private static final String TAG = "com.parse.ParsePushReceiver";

  /**
   * The name of the Intent extra which contains a channel used to route this notification.
   * May be {@code null}.
   * */
  public static final String KEY_PUSH_CHANNEL = "com.parse.Channel";

  /** The name of the Intent extra which contains the JSON payload of the Notification. */
  public static final String KEY_PUSH_DATA = "com.parse.Data";

  /** The name of the Intent fired when a push has been received. */
  public static final String ACTION_PUSH_RECEIVE = "com.parse.push.intent.RECEIVE";

  /** The name of the Intent fired when a notification has been opened. */
  public static final String ACTION_PUSH_OPEN = "com.parse.push.intent.OPEN";

  /** The name of the Intent fired when a notification has been dismissed. */
  public static final String ACTION_PUSH_DELETE = "com.parse.push.intent.DELETE";

  /** The name of the meta-data field used to override the icon used in Notifications. */
  public static final String PROPERTY_PUSH_ICON = "com.parse.push.notification_icon";

  protected static final int SMALL_NOTIFICATION_MAX_CHARACTER_LIMIT = 38;

  private static final List<String> REQUIRED_ACTIONS = Arrays.asList(
      ACTION_PUSH_RECEIVE, ACTION_PUSH_OPEN, ACTION_PUSH_DELETE);

  /**
   * Called at startup at the moment of parsing the manifest, to see
   * if it was correctly set-up.
   */
  static boolean isSupported() {
    int actions = 0;
    for (String action : REQUIRED_ACTIONS) {
      if (ManifestInfo.hasIntentReceiver(action)) actions++;
    }

    if (actions < REQUIRED_ACTIONS.size()) {
      if (actions > 0) {
        throw new IllegalStateException(
            "The Parse Push BroadcastReceiver must implement a filter for all of " +
                ParsePushBroadcastReceiver.ACTION_PUSH_RECEIVE + ", " +
                ParsePushBroadcastReceiver.ACTION_PUSH_OPEN + ", and " +
                ParsePushBroadcastReceiver.ACTION_PUSH_DELETE);
      } else {
        PLog.e(TAG, "Push is currently disabled. Parse SDK requires your app to " +
            "have a BroadcastReceiver that handles " +
            ParsePushBroadcastReceiver.ACTION_PUSH_RECEIVE + ", " +
            ParsePushBroadcastReceiver.ACTION_PUSH_OPEN + ", and " +
            ParsePushBroadcastReceiver.ACTION_PUSH_DELETE + ". You can do this by adding " +
            "these lines to your AndroidManifest.xml:\n\n" +
            " <receiver android:name=\"com.parse.ParsePushBroadcastReceiver\"\n" +
            "   android:exported=false>\n" +
            "  <intent-filter>\n" +
            "     <action android:name=\"com.parse.push.intent.RECEIVE\" />\n" +
            "     <action android:name=\"com.parse.push.intent.OPEN\" />\n" +
            "     <action android:name=\"com.parse.push.intent.DELETE\" />\n" +
            "   </intent-filter>\n" +
            " </receiver>");
      }
      return false;
    }
    return true;
  }

  /**
   * Delegates the generic {@code onReceive} event to a notification lifecycle event.
   * Subclasses are advised to override the lifecycle events and not this method.
   *
   * @param context
   *      The {@code Context} in which the receiver is running.
   * @param intent
   *      An {@code Intent} containing the channel and data of the current push notification.
   *
   * @see ParsePushBroadcastReceiver#onPushReceive(Context, Intent)
   * @see ParsePushBroadcastReceiver#onPushOpen(Context, Intent)
   * @see ParsePushBroadcastReceiver#onPushDismiss(Context, Intent)
   */
  @Override
  public void onReceive(Context context, Intent intent) {
    String intentAction = intent.getAction();
    switch (intentAction) {
      case ACTION_PUSH_RECEIVE:
        onPushReceive(context, intent);
        break;
      case ACTION_PUSH_DELETE:
        onPushDismiss(context, intent);
        break;
      case ACTION_PUSH_OPEN:
        onPushOpen(context, intent);
        break;
    }
  }

  /**
   * Called when the push notification is received. By default, a broadcast intent will be sent if
   * an "action" is present in the data and a notification will be show if "alert" and "title" are
   * present in the data.
   *
   * @param context
   *      The {@code Context} in which the receiver is running.
   * @param intent
   *      An {@code Intent} containing the channel and data of the current push notification.
   */
  protected void onPushReceive(Context context, Intent intent) {
    String pushDataStr = intent.getStringExtra(KEY_PUSH_DATA);
    if (pushDataStr == null) {
      PLog.e(TAG, "Can not get push data from intent.");
      return;
    }
    PLog.v(TAG, "Received push data: " + pushDataStr);

    JSONObject pushData = null;
    try {
      pushData = new JSONObject(pushDataStr);
    } catch (JSONException e) {
      PLog.e(TAG, "Unexpected JSONException when receiving push data: ", e);
    }

    // If the push data includes an action string, that broadcast intent is fired.
    String action = null;
    if (pushData != null) {
      action = pushData.optString("action", null);
    }
    if (action != null) {
      Bundle extras = intent.getExtras();
      Intent broadcastIntent = new Intent();
      broadcastIntent.putExtras(extras);
      broadcastIntent.setAction(action);
      broadcastIntent.setPackage(context.getPackageName());
      context.sendBroadcast(broadcastIntent);
    }

    Notification notification = getNotification(context, intent);

    if (notification != null) {
      ParseNotificationManager.getInstance().showNotification(context, notification);
    }
  }

  /**
   * Called when the push notification is dismissed. By default, nothing is performed
   * on notification dismissal.
   *
   * @param context
   *      The {@code Context} in which the receiver is running.
   * @param intent
   *      An {@code Intent} containing the channel and data of the current push notification.
   */
  protected void onPushDismiss(Context context, Intent intent) {
    // do nothing
  }

  /**
   * Called when the push notification is opened by the user. Sends analytics info back to Parse
   * that the application was opened from this push notification. By default, this will navigate
   * to the {@link Activity} returned by {@link #getActivity(Context, Intent)}. If the push contains
   * a 'uri' parameter, an Intent is fired to view that URI with the Activity returned by
   * {@link #getActivity} in the back stack.
   *
   * @param context
   *      The {@code Context} in which the receiver is running.
   * @param intent
   *      An {@code Intent} containing the channel and data of the current push notification.
   */
  protected void onPushOpen(Context context, Intent intent) {
    // Send a Parse Analytics "push opened" event
    ParseAnalytics.trackAppOpenedInBackground(intent);

    String uriString = null;
    try {
      JSONObject pushData = new JSONObject(intent.getStringExtra(KEY_PUSH_DATA));
      uriString = pushData.optString("uri", null);
    } catch (JSONException e) {
      PLog.e(TAG, "Unexpected JSONException when receiving push data: ", e);
    }

    Class<? extends Activity> cls = getActivity(context, intent);
    Intent activityIntent;
    if (uriString != null) {
      activityIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(uriString));
    } else {
      activityIntent = new Intent(context, cls);
    }

    activityIntent.putExtras(intent.getExtras());
    /*
      In order to remove dependency on android-support-library-v4
      The reason why we differentiate between versions instead of just using context.startActivity
      for all devices is because in API 11 the recommended conventions for app navigation using
      the back key changed.
     */
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
      TaskStackBuilderHelper.startActivities(context, cls, activityIntent);
    } else {
      activityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      activityIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
      context.startActivity(activityIntent);
    }
  }

  /**
   * Used by {@link #onPushOpen} to determine which activity to launch or insert into the back
   * stack. The default implementation retrieves the launch activity class for the package.
   *
   * @param context
   *      The {@code Context} in which the receiver is running.
   * @param intent
   *      An {@code Intent} containing the channel and data of the current push notification.
   * @return
   *      The default {@code Activity} class of the package or {@code null} if no launch intent is
   *      defined in {@code AndroidManifest.xml}.
   */
  protected Class<? extends Activity> getActivity(Context context, Intent intent) {
    String packageName = context.getPackageName();
    Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(packageName);
    if (launchIntent == null) {
      return null;
    }
    String className = launchIntent.getComponent().getClassName();
    Class<? extends Activity> cls = null;
    try {
      cls = (Class <? extends Activity>)Class.forName(className);
    } catch (ClassNotFoundException e) {
      // do nothing
    }
    return cls;
  }

  /**
   * Retrieves the channel to be used in a {@link Notification} if API >= 26, if not null. The default returns a new channel
   * with id "parse_push", name "Push notifications" and default importance.
   *
   * @param context
   *      The {@code Context} in which the receiver is running.
   * @param intent
   *      An {@code Intent} containing the channel and data of the current push notification.
   * @return
   *      The notification channel
   */
  @TargetApi(Build.VERSION_CODES.O)
  protected NotificationChannel getNotificationChannel(Context context, Intent intent) {
    return new NotificationChannel("parse_push", "Push notifications", NotificationManager.IMPORTANCE_DEFAULT);
  }

  /**
   * Creates the notification channel with the NotificationManager. Channel is not recreated
   * if the channel properties are unchanged.
   *
   * @param context
   *      The {@code Context} in which the receiver is running.
   * @param notificationChannel
   *      The {@code NotificationChannel} to be created.
   */
  @TargetApi(Build.VERSION_CODES.O)
  protected void createNotificationChannel(Context context, NotificationChannel notificationChannel) {
    NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    nm.createNotificationChannel(notificationChannel);
  }

  /**
   * Retrieves the small icon to be used in a {@link Notification}. The default implementation uses
   * the icon specified by {@code com.parse.push.notification_icon} {@code meta-data} in your
   * {@code AndroidManifest.xml} with a fallback to the launcher icon for this package. To conform
   * to Android style guides, it is highly recommended that developers specify an explicit push
   * icon.
   *
   * @param context
   *      The {@code Context} in which the receiver is running.
   * @param intent
   *      An {@code Intent} containing the channel and data of the current push notification.
   * @return
   *      The resource id of the default small icon for the package
   *
   * @see <a href="http://developer.android.com/design/style/iconography.html#notification">Android Notification Style Guide</a>
   */
  protected int getSmallIconId(Context context, Intent intent) {
    Bundle metaData = ManifestInfo.getApplicationMetadata(context);
    int explicitId = 0;
    if (metaData != null) {
      explicitId = metaData.getInt(PROPERTY_PUSH_ICON);
    }
    return explicitId != 0 ? explicitId : ManifestInfo.getIconId();
  }

  /**
   * Retrieves the large icon to be used in a {@link Notification}. This {@code Bitmap} should be
   * used to provide special context for a particular {@link Notification}, such as the avatar of
   * user who generated the {@link Notification}. The default implementation returns {@code null},
   * causing the {@link Notification} to display only the small icon.
   *
   * @param context
   *      The {@code Context} in which the receiver is running.
   * @param intent
   *      An {@code Intent} containing the channel and data of the current push notification.
   * @return
   *      Bitmap of the default large icon for the package
   *
   * @see <a href="http://developer.android.com/guide/topics/ui/notifiers/notifications.html#NotificationUI">Android Notification UI Overview</a>
   */
  protected Bitmap getLargeIcon(Context context, Intent intent) {
    return null;
  }

  private JSONObject getPushData(Intent intent) {
    try {
      return new JSONObject(intent.getStringExtra(KEY_PUSH_DATA));
    } catch (JSONException e) {
      PLog.e(TAG, "Unexpected JSONException when receiving push data: ", e);
      return null;
    }
  }
  /**
   * Creates a {@link Notification} with reasonable defaults. If "alert" and "title" are
   * both missing from data, then returns {@code null}. If the text in the notification is longer
   * than 38 characters long, the style of the notification will be set to
   * {@link android.app.Notification.BigTextStyle}.
   * <p/>
   * As a security precaution, developers overriding this method should be sure to set the package
   * on notification {@code Intent}s to avoid leaking information to other apps.
   *
   * @param context
   *      The {@code Context} in which the receiver is running.
   * @param intent
   *      An {@code Intent} containing the channel and data of the current push notification.
   * @return
   *      The notification to be displayed.
   *
   * @see ParsePushBroadcastReceiver#onPushReceive(Context, Intent)
   */
  protected Notification getNotification(Context context, Intent intent) {
    JSONObject pushData = getPushData(intent);
    if (pushData == null || (!pushData.has("alert") && !pushData.has("title"))) {
      return null;
    }

    String title = pushData.optString("title", ManifestInfo.getDisplayName(context));
    String alert = pushData.optString("alert", "Notification received.");
    String tickerText = String.format(Locale.getDefault(), "%s: %s", title, alert);

    Bundle extras = intent.getExtras();

    Random random = new Random();
    int contentIntentRequestCode = random.nextInt();
    int deleteIntentRequestCode = random.nextInt();

    // Security consideration: To protect the app from tampering, we require that intent filters
    // not be exported. To protect the app from information leaks, we restrict the packages which
    // may intercept the push intents.
    String packageName = context.getPackageName();

    Intent contentIntent = new Intent(ParsePushBroadcastReceiver.ACTION_PUSH_OPEN);
    contentIntent.putExtras(extras);
    contentIntent.setPackage(packageName);

    Intent deleteIntent = new Intent(ParsePushBroadcastReceiver.ACTION_PUSH_DELETE);
    deleteIntent.putExtras(extras);
    deleteIntent.setPackage(packageName);

    PendingIntent pContentIntent = PendingIntent.getBroadcast(context, contentIntentRequestCode,
        contentIntent, PendingIntent.FLAG_UPDATE_CURRENT);
    PendingIntent pDeleteIntent = PendingIntent.getBroadcast(context, deleteIntentRequestCode,
        deleteIntent, PendingIntent.FLAG_UPDATE_CURRENT);



    // The purpose of setDefaults(Notification.DEFAULT_ALL) is to inherit notification properties
    // from system defaults
    NotificationCompat.Builder parseBuilder = new NotificationCompat.Builder(context);
    parseBuilder.setContentTitle(title)
        .setContentText(alert)
        .setTicker(tickerText)
        .setSmallIcon(this.getSmallIconId(context, intent))
        .setLargeIcon(this.getLargeIcon(context, intent))
        .setContentIntent(pContentIntent)
        .setDeleteIntent(pDeleteIntent)
        .setAutoCancel(true)
        .setDefaults(Notification.DEFAULT_ALL);

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      NotificationChannel notificationChannel = getNotificationChannel(context, intent);
      createNotificationChannel(context, notificationChannel);
      parseBuilder.setNotificationChannel(notificationChannel.getId());
    }

    if (alert != null
        && alert.length() > ParsePushBroadcastReceiver.SMALL_NOTIFICATION_MAX_CHARACTER_LIMIT) {
      parseBuilder.setStyle(new NotificationCompat.Builder.BigTextStyle().bigText(alert));
    }
    return parseBuilder.build();
  }
}

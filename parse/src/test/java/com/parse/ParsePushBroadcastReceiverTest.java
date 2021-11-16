package com.parse;

import static androidx.core.app.NotificationCompat.EXTRA_TEXT;
import static androidx.core.app.NotificationCompat.EXTRA_TITLE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.Intent;
import androidx.core.app.NotificationCompat;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public class ParsePushBroadcastReceiverTest extends ResetPluginsParseTest {

    @Before
    public void setUp() throws Exception {
        super.setUp();

        Parse.Configuration configuration =
                new Parse.Configuration.Builder(RuntimeEnvironment.application)
                        .applicationId(BuildConfig.LIBRARY_PACKAGE_NAME)
                        .server("https://api.parse.com/1")
                        .build();

        ParsePlugins plugins = mock(ParsePlugins.class);
        when(plugins.configuration()).thenReturn(configuration);
        when(plugins.applicationContext()).thenReturn(RuntimeEnvironment.application);
        Parse.initialize(configuration, plugins);
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
        ParseCorePlugins.getInstance().reset();
        ParsePlugins.reset();
        Parse.destroy();
    }

    @Test
    public void testBuildNotification() {
        final ParsePushBroadcastReceiver broadcastReceiver = new ParsePushBroadcastReceiver();
        final Map<String, String> map = new HashMap();
        map.put("alert", "alert");
        map.put("title", "title");

        final String notificationPayload = new JSONObject(map).toString();

        final Intent intent = new Intent();
        intent.putExtra(ParsePushBroadcastReceiver.KEY_PUSH_DATA, notificationPayload);

        final NotificationCompat.Builder notification =
                broadcastReceiver.getNotification(
                        RuntimeEnvironment.getApplication().getApplicationContext(), intent);

        assertNotNull(notification);
    }

    @Test
    public void testBuildNotificationReturnsNullOnInvalidPayload() {
        final ParsePushBroadcastReceiver broadcastReceiver = new ParsePushBroadcastReceiver();
        final Intent intent = new Intent();
        intent.putExtra(ParsePushBroadcastReceiver.KEY_PUSH_DATA, "{\"json\": broken json}");

        final NotificationCompat.Builder notification =
                broadcastReceiver.getNotification(
                        RuntimeEnvironment.getApplication().getApplicationContext(), intent);

        assertNull(notification);
    }

    @Test
    public void testBuildNotificationReturnsNullOnMissingTitleAndAlert() {
        final ParsePushBroadcastReceiver broadcastReceiver = new ParsePushBroadcastReceiver();
        final Map<String, String> map = new HashMap();

        final String notificationPayload = new JSONObject(map).toString();

        final Intent intent = new Intent();
        intent.putExtra(ParsePushBroadcastReceiver.KEY_PUSH_DATA, notificationPayload);

        final NotificationCompat.Builder notification =
                broadcastReceiver.getNotification(
                        RuntimeEnvironment.getApplication().getApplicationContext(), intent);

        assertNull(notification);
    }

    @Test
    public void testNotificationBuilderWhenAlertNotProvidedSetFallback() {
        final ParsePushBroadcastReceiver broadcastReceiver = new ParsePushBroadcastReceiver();
        final Map<String, String> map = new HashMap();
        map.put("title", "title");

        final String notificationPayload = new JSONObject(map).toString();

        final Intent intent = new Intent();
        intent.putExtra(ParsePushBroadcastReceiver.KEY_PUSH_DATA, notificationPayload);

        final NotificationCompat.Builder notification =
                broadcastReceiver.getNotification(
                        RuntimeEnvironment.getApplication().getApplicationContext(), intent);

        assertNotNull(notification);
        assertEquals("Notification received.", notification.build().extras.getString(EXTRA_TEXT));
    }

    @Test
    public void testNotificationBuilderWhenAlertIsSetWhenProvided() {
        final ParsePushBroadcastReceiver broadcastReceiver = new ParsePushBroadcastReceiver();
        final Map<String, String> map = new HashMap();
        map.put("alert", "This is an alert");

        final String notificationPayload = new JSONObject(map).toString();

        final Intent intent = new Intent();
        intent.putExtra(ParsePushBroadcastReceiver.KEY_PUSH_DATA, notificationPayload);

        final NotificationCompat.Builder notification =
                broadcastReceiver.getNotification(
                        RuntimeEnvironment.getApplication().getApplicationContext(), intent);

        assertNotNull(notification);
        assertEquals("This is an alert", notification.build().extras.getString(EXTRA_TEXT));
    }

    @Test
    public void testNotificationBuilderWhenTitleNotProvidedSetFallback() {
        final ParsePushBroadcastReceiver broadcastReceiver = new ParsePushBroadcastReceiver();
        final Map<String, String> map = new HashMap();
        map.put("alert", "alert");

        final String notificationPayload = new JSONObject(map).toString();

        final Intent intent = new Intent();
        intent.putExtra(ParsePushBroadcastReceiver.KEY_PUSH_DATA, notificationPayload);

        final NotificationCompat.Builder notification =
                broadcastReceiver.getNotification(
                        RuntimeEnvironment.getApplication().getApplicationContext(), intent);

        assertNotNull(notification);
        assertEquals("com.parse.test", notification.build().extras.getString(EXTRA_TITLE));
    }

    @Test
    public void testNotificationBuilderWhenTitleIsSetWhenProvided() {
        final ParsePushBroadcastReceiver broadcastReceiver = new ParsePushBroadcastReceiver();
        final Map<String, String> map = new HashMap();
        map.put("title", "Application name");

        final String notificationPayload = new JSONObject(map).toString();

        final Intent intent = new Intent();
        intent.putExtra(ParsePushBroadcastReceiver.KEY_PUSH_DATA, notificationPayload);

        final NotificationCompat.Builder notification =
                broadcastReceiver.getNotification(
                        RuntimeEnvironment.getApplication().getApplicationContext(), intent);

        assertNotNull(notification);
        assertEquals("Application name", notification.build().extras.getString(EXTRA_TITLE));
    }
}

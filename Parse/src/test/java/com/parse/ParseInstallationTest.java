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
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.res.builder.RobolectricPackageManager;

import java.util.Arrays;
import java.util.Locale;
import java.util.TimeZone;

import bolts.Task;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(RobolectricGradleTestRunner.class)
@Config(constants = BuildConfig.class, sdk = 21)
public class ParseInstallationTest {
  private static final String KEY_INSTALLATION_ID = "installationId";
  private static final String KEY_DEVICE_TYPE = "deviceType";
  private static final String KEY_APP_NAME = "appName";
  private static final String KEY_APP_IDENTIFIER = "appIdentifier";
  private static final String KEY_TIME_ZONE = "timeZone";
  private static final String KEY_LOCALE_IDENTIFIER = "localeIdentifier";
  private static final String KEY_APP_VERSION = "appVersion";

  private Locale defaultLocale;

  @Before
  public void setUp() {
    ParseObject.registerSubclass(ParseInstallation.class);

    defaultLocale = Locale.getDefault();
  }

  @After
  public void tearDown() {
    ParseObject.unregisterSubclass(ParseInstallation.class);
    ParseCorePlugins.getInstance().reset();
    ParsePlugins.reset();

    Locale.setDefault(defaultLocale);
  }

  @Test
  public void testImmutableKeys() {
    String[] immutableKeys = {
        "installationId",
        "deviceType",
        "appName",
        "appIdentifier",
        "parseVersion",
        "deviceToken",
        "deviceTokenLastModified",
        "pushType",
        "timeZone",
        "localeIdentifier",
        "appVersion"
    };

    ParseInstallation installation = new ParseInstallation();
    installation.put("foo", "bar");

    for (String immutableKey : immutableKeys) {
      try {
        installation.put(immutableKey, "blah");
      } catch (IllegalArgumentException e) {
        assertTrue(e.getMessage().contains("Cannot modify"));
      }

      try {
        installation.remove(immutableKey);
      } catch (IllegalArgumentException e) {
        assertTrue(e.getMessage().contains("Cannot modify"));
      }

      try {
        installation.removeAll(immutableKey, Arrays.asList());
      } catch (IllegalArgumentException e) {
        assertTrue(e.getMessage().contains("Cannot modify"));
      }
    }
  }

  @Test
  public void testHandleSaveResultAsync() throws Exception {
    // Mock currentInstallationController to make setAsync work
    ParseCurrentInstallationController controller =
        mock(ParseCurrentInstallationController.class);
    when(controller.setAsync(any(ParseInstallation.class))).thenReturn(Task.<Void>forResult(null));
    ParseCorePlugins.getInstance().registerCurrentInstallationController(controller);
    // Mock return state
    ParseInstallation.State state = new ParseInstallation.State.Builder("_Installation")
        .put("key", "value")
        .build();

    ParseInstallation installation = new ParseInstallation();
    installation.put("keyAgain", "valueAgain");
    ParseOperationSet operationSet = installation.startSave();
    ParseTaskUtils.wait(installation.handleSaveResultAsync(state, operationSet));

    // Make sure the installation data is correct
    assertEquals("value", installation.get("key"));
    assertEquals("valueAgain", installation.get("keyAgain"));
    // Make sure we set the currentInstallation
    verify(controller, times(1)).setAsync(installation);
  }

  @Test
  public void testHandleFetchResultAsync() throws Exception {
    // Mock currentInstallationController to make setAsync work
    ParseCurrentInstallationController controller =
        mock(ParseCurrentInstallationController.class);
    when(controller.setAsync(any(ParseInstallation.class))).thenReturn(Task.<Void>forResult(null));
    ParseCorePlugins.getInstance().registerCurrentInstallationController(controller);
    // Mock return state
    ParseInstallation.State state = new ParseInstallation.State.Builder("_Installation")
        .put("key", "value")
        .isComplete(true)
        .build();

    ParseInstallation installation = new ParseInstallation();
    ParseTaskUtils.wait(installation.handleFetchResultAsync(state));

    // Make sure the installation data is correct
    assertEquals("value", installation.get("key"));
    // Make sure we set the currentInstallation
    verify(controller, times(1)).setAsync(installation);
  }

  @Test
  public void testUpdateBeforeSave() throws Exception {
    mocksForUpdateBeforeSave();

    Locale.setDefault(new Locale("en", "US"));

    ParseInstallation installation = new ParseInstallation();
    installation.updateBeforeSave();

    // Make sure we update timezone
    String zone = TimeZone.getDefault().getID();
    assertEquals(zone, installation.getString(KEY_TIME_ZONE));
    // Make sure we update version info
    Context context = Parse.getApplicationContext();
    String packageName = context.getPackageName();
    PackageManager pm = context.getPackageManager();
    PackageInfo pkgInfo = pm.getPackageInfo(packageName, 0);
    String appVersion = pkgInfo.versionName;
    String appName = pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString();
    assertEquals(packageName, installation.getString(KEY_APP_IDENTIFIER));
    assertEquals(appName, installation.getString(KEY_APP_NAME));
    assertEquals(appVersion, installation.getString(KEY_APP_VERSION));
    // Make sure we update device info
    assertEquals("android", installation.getString(KEY_DEVICE_TYPE));
    assertEquals("installationId", installation.getString(KEY_INSTALLATION_ID));
    // Make sure we update the locale identifier
    assertEquals("en-US", installation.getString(KEY_LOCALE_IDENTIFIER));
  }

  // TODO(mengyan): Add other testUpdateBeforeSave cases to cover all branches

  @Test
  public void testPushType() throws Exception {
    ParseInstallation installation = new ParseInstallation();
    installation.setPushType(PushType.GCM);

    assertEquals(PushType.GCM, installation.getPushType());

    installation.removePushType();

    assertNull(installation.getPushType());
    // Make sure we add the pushType to operationSetQueue instead of serverData
    assertEquals(1, installation.operationSetQueue.getLast().size());
  }

  @Test
  public void testPushTypeWithNullPushType() throws Exception {
    ParseInstallation installation = new ParseInstallation();
    installation.setPushType(PushType.GCM);

    assertEquals(PushType.GCM, installation.getPushType());

    installation.setPushType(null);

    assertEquals(PushType.GCM, installation.getPushType());
  }

  @Test
  public void testDeviceToken() throws Exception {
    ParseInstallation installation = new ParseInstallation();
    installation.setDeviceToken("deviceToken");

    assertEquals("deviceToken", installation.getDeviceToken());

    installation.removeDeviceToken();

    assertNull(installation.getDeviceToken());
    // Make sure we add the pushType to operationSetQueue instead of serverData
    assertEquals(1, installation.operationSetQueue.getLast().size());
  }

  @Test
  public void testDeviceTokenWithNullDeviceToken() throws Exception {
    ParseInstallation installation = new ParseInstallation();
    installation.setDeviceToken("deviceToken");

    assertEquals("deviceToken", installation.getDeviceToken());

    installation.setDeviceToken(null);

    assertEquals("deviceToken", installation.getDeviceToken());
  }

  @Test
  public void testGetCurrentInstallation() throws Exception {
    // Mock currentInstallationController to make setAsync work
    ParseCurrentInstallationController controller =
        mock(ParseCurrentInstallationController.class);
    ParseInstallation currentInstallation = new ParseInstallation();
    when(controller.getAsync()).thenReturn(Task.forResult(currentInstallation));
    ParseCorePlugins.getInstance().registerCurrentInstallationController(controller);

    ParseInstallation installation = ParseInstallation.getCurrentInstallation();

    assertEquals(currentInstallation, installation);
    verify(controller, times(1)).getAsync();
  }

  @Test
  public void testLocaleIdentifierSpecialCases() throws Exception {
    mocksForUpdateBeforeSave();

    ParseInstallation installation = new ParseInstallation();

    // Deprecated two-letter codes (Java issue).
    Locale.setDefault(new Locale("iw", "US"));
    installation.updateBeforeSave();
    assertEquals("he-US", installation.getString(KEY_LOCALE_IDENTIFIER));

    Locale.setDefault(new Locale("in", "US"));
    installation.updateBeforeSave();
    assertEquals("id-US", installation.getString(KEY_LOCALE_IDENTIFIER));

    Locale.setDefault(new Locale("ji", "US"));
    installation.updateBeforeSave();
    assertEquals("yi-US", installation.getString(KEY_LOCALE_IDENTIFIER));

    // No country code.
    Locale.setDefault(new Locale("en"));
    installation.updateBeforeSave();
    assertEquals("en", installation.getString(KEY_LOCALE_IDENTIFIER));
  }



  // TODO(mengyan): Add testFetchAsync, right now we can not test super methods inside
  // testFetchAsync

  private static void mocksForUpdateBeforeSave() {
    // Mock currentInstallationController to make setAsync work
    ParseCurrentInstallationController controller =
            mock(ParseCurrentInstallationController.class);
    when(controller.isCurrent(any(ParseInstallation.class))).thenReturn(true);
    ParseCorePlugins.getInstance().registerCurrentInstallationController(controller);
    // Mock package manager
    RobolectricPackageManager packageManager =
            spy(RuntimeEnvironment.getRobolectricPackageManager());
    doReturn("parseTest").when(packageManager).getApplicationLabel(any(ApplicationInfo.class));
    RuntimeEnvironment.setRobolectricPackageManager(packageManager);
    ParsePlugins.Android plugins = mock(ParsePlugins.Android.class);
    // Mock installationId
    InstallationId installationId = mock(InstallationId.class);
    when(installationId.get()).thenReturn("installationId");
    when(plugins.installationId()).thenReturn(installationId);
    // Mock application context
    when(plugins.applicationContext()).thenReturn(RuntimeEnvironment.application);
    ParsePlugins.set(plugins);
  }
}

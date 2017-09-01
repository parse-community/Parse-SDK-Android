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
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.Locale;
import java.util.TimeZone;

import bolts.Task;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
@Config(constants = BuildConfig.class, sdk = TestHelper.ROBOLECTRIC_SDK_VERSION)
public class ParseInstallationTest extends ResetPluginsParseTest {
  private static final String KEY_INSTALLATION_ID = "installationId";
  private static final String KEY_DEVICE_TYPE = "deviceType";
  private static final String KEY_APP_NAME = "appName";
  private static final String KEY_APP_IDENTIFIER = "appIdentifier";
  private static final String KEY_TIME_ZONE = "timeZone";
  private static final String KEY_LOCALE_IDENTIFIER = "localeIdentifier";
  private static final String KEY_APP_VERSION = "appVersion";

  private Locale defaultLocale;

  @Before
  public void setUp() throws Exception {
    super.setUp();
    ParseObject.registerSubclass(ParseInstallation.class);

    defaultLocale = Locale.getDefault();
  }

  @After
  public void tearDown() throws Exception {
    super.tearDown();
    ParseObject.unregisterSubclass(ParseInstallation.class);

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

  @Test (expected = RuntimeException.class)
  public void testInstallationObjectIdCannotBeChanged() throws Exception {
    boolean hasException = false;
    ParseInstallation installation = new ParseInstallation();
    try {
      installation.put("objectId", "abc");
    } catch (IllegalArgumentException e) {
      assertTrue(e.getMessage().contains("Cannot modify"));
      hasException = true;
    }
    assertTrue(hasException);
    installation.setObjectId("abc");
  }

  @Test
  public void testMissingRequiredFieldWhenSaveAsync() throws Exception {
    String sessionToken = "sessionToken";
    Task<Void> toAwait = Task.forResult(null);

    ParseCurrentInstallationController controller = mockCurrentInstallationController();

    ParseObjectController objController = mock(ParseObjectController.class);
    // mock return task when Installation was deleted on the server
    Task<ParseObject.State> taskError = Task.forError(new ParseException(ParseException.MISSING_REQUIRED_FIELD_ERROR, ""));
    // mock return task when Installation was re-saved to the server
    Task<ParseObject.State> task = Task.forResult(null);
    when(objController.saveAsync(
        any(ParseObject.State.class),
        any(ParseOperationSet.class),
        eq(sessionToken),
        any(ParseDecoder.class)))
        .thenReturn(taskError)
        .thenReturn(task);
    ParseCorePlugins.getInstance()
        .registerObjectController(objController);

    ParseInstallation installation = ParseInstallation.getCurrentInstallation();
    assertNotNull(installation);
    installation.put("key", "value");
    installation.saveAsync(sessionToken, toAwait);
    verify(controller).getAsync();
    verify(objController, times(2)).saveAsync(
        any(ParseObject.State.class),
        any(ParseOperationSet.class),
        eq(sessionToken),
        any(ParseDecoder.class));
  }

  @Test
  public void testObjectNotFoundWhenSaveAsync() throws Exception {
    OfflineStore lds = new OfflineStore(RuntimeEnvironment.application);
    Parse.setLocalDatastore(lds);

    String sessionToken = "sessionToken";
    Task<Void> toAwait = Task.forResult(null);

    ParseCurrentInstallationController controller = mockCurrentInstallationController();
    ParseObjectController objController = mock(ParseObjectController.class);
    // mock return task when Installation was deleted on the server
    Task<ParseObject.State> taskError = Task.forError(new ParseException(ParseException.OBJECT_NOT_FOUND, ""));
    // mock return task when Installation was re-saved to the server
    Task<ParseObject.State> task = Task.forResult(null);
    when(objController.saveAsync(
        any(ParseObject.State.class),
        any(ParseOperationSet.class),
        eq(sessionToken),
        any(ParseDecoder.class)))
        .thenReturn(taskError)
        .thenReturn(task);
    ParseCorePlugins.getInstance()
        .registerObjectController(objController);

    ParseObject.State state = new ParseObject.State.Builder("_Installation")
        .objectId("oldId")
        .put("deviceToken", "deviceToken")
        .build();
    ParseInstallation installation = ParseInstallation.getCurrentInstallation();
    assertNotNull(installation);
    installation.setState(state);
    installation.put("key", "value");
    installation.saveAsync(sessionToken, toAwait);

    verify(controller).getAsync();
    verify(objController, times(2)).saveAsync(
        any(ParseObject.State.class),
        any(ParseOperationSet.class),
        eq(sessionToken),
        any(ParseDecoder.class));
    Parse.setLocalDatastore(null);
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
    String zone = installation.getString(KEY_TIME_ZONE);
    String deviceZone = TimeZone.getDefault().getID();
    if (zone != null) {
        assertEquals(zone, deviceZone);
    } else {
        // If it's not updated it's because it was not acceptable.
        assertFalse(deviceZone.equals("GMT"));
        assertFalse(deviceZone.indexOf("/") > 0);
    }

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
    // Mock App Name
    RuntimeEnvironment.application.getApplicationInfo().name = "parseTest";
    ParsePlugins.Android plugins = mock(ParsePlugins.Android.class);
    // Mock installationId
    InstallationId installationId = mock(InstallationId.class);
    when(installationId.get()).thenReturn("installationId");
    when(plugins.installationId()).thenReturn(installationId);
    // Mock application context
    when(plugins.applicationContext()).thenReturn(RuntimeEnvironment.application);
    ParsePlugins.set(plugins);
  }

  private ParseCurrentInstallationController mockCurrentInstallationController() {
    ParseCurrentInstallationController controller =
        mock(ParseCurrentInstallationController.class);
    ParseInstallation currentInstallation = new ParseInstallation();
    when(controller.getAsync())
        .thenReturn(Task.forResult(currentInstallation));
    ParseCorePlugins.getInstance()
        .registerCurrentInstallationController(controller);
    return controller;
  }
}

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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;

import bolts.Continuation;
import bolts.Task;

/**
 * The {@code ParseInstallation} is a local representation of installation data that can be saved
 * and retrieved from the Parse cloud.
 */
@ParseClassName("_Installation")
public class ParseInstallation extends ParseObject {
  private static final String TAG = "com.parse.ParseInstallation";

  private static final String KEY_INSTALLATION_ID = "installationId";
  private static final String KEY_DEVICE_TYPE = "deviceType";
  private static final String KEY_APP_NAME = "appName";
  private static final String KEY_APP_IDENTIFIER = "appIdentifier";
  private static final String KEY_PARSE_VERSION = "parseVersion";
  private static final String KEY_DEVICE_TOKEN = "deviceToken";
  private static final String KEY_PUSH_TYPE = "pushType";
  private static final String KEY_TIME_ZONE = "timeZone";
  private static final String KEY_APP_VERSION = "appVersion";
  /* package */ static final String KEY_CHANNELS = "channels";

  private static final List<String> READ_ONLY_FIELDS = Collections.unmodifiableList(
      Arrays.asList(KEY_DEVICE_TYPE, KEY_INSTALLATION_ID, KEY_DEVICE_TOKEN, KEY_PUSH_TYPE,
          KEY_TIME_ZONE, KEY_APP_VERSION, KEY_APP_NAME, KEY_PARSE_VERSION,
          KEY_APP_IDENTIFIER));

  // TODO(mengyan): Inject into ParseInstallationInstanceController
  /* package */ static ParseCurrentInstallationController getCurrentInstallationController() {
    return ParseCorePlugins.getInstance().getCurrentInstallationController();
  }

  public static ParseInstallation getCurrentInstallation() {
    try {
      return ParseTaskUtils.wait(
          getCurrentInstallationController().getAsync());
    } catch (ParseException e) {
      // In order to have backward compatibility, we swallow the exception silently.
      return null;
    }
  }

  /**
   * Constructs a query for {@code ParseInstallation}.
   * <p/>
   * <strong>Note:</strong> We only allow the following types of queries for installations:
   * <pre>
   * query.get(objectId)
   * query.whereEqualTo("installationId", value)
   * query.whereMatchesKeyInQuery("installationId", keyInQuery, query)
   * </pre>
   * <p/>
   * You can add additional query clauses, but one of the above must appear as a top-level
   * {@code AND} clause in the query.
   *
   * @see com.parse.ParseQuery#getQuery(Class)
   */
  public static ParseQuery<ParseInstallation> getQuery() {
    return ParseQuery.getQuery(ParseInstallation.class);
  }

  public ParseInstallation() {
    // do nothing
  }

  /**
   * Returns the unique ID of this installation.
   *
   * @return A UUID that represents this device.
   */
  public String getInstallationId() {
    return getString(KEY_INSTALLATION_ID);
  }

  @Override
  /* package */ boolean needsDefaultACL() {
    return false;
  }

  @Override
  /* package */ boolean isKeyMutable(String key) {
    return !READ_ONLY_FIELDS.contains(key);
  }

  @Override
  /* package */ void updateBeforeSave() {
    super.updateBeforeSave();
    if (getCurrentInstallationController().isCurrent(ParseInstallation.this)) {
      updateTimezone();
      updateVersionInfo();
      updateDeviceInfo();
    }
  }

  @Override
  /* package */ <T extends ParseObject> Task<T> fetchAsync(
      final String sessionToken, final Task<Void> toAwait) {
    synchronized (mutex) {
      // Because the Service and the global currentInstallation are different objects, we may not
      // have the same ObjectID (we never will at bootstrap). The server has a special hack for
      // _Installation where save with an existing InstallationID will merge Object IDs
      Task<Void> result;
      if (getObjectId() == null) {
        result = saveAsync(sessionToken, toAwait);
      } else {
        result = Task.forResult(null);
      }
      return result.onSuccessTask(new Continuation<Void, Task<T>>() {
        @Override
        public Task<T> then(Task<Void> task) throws Exception {
          return ParseInstallation.super.fetchAsync(sessionToken, toAwait);
        }
      });
    }
  }

  @Override
  /* package */ Task<Void> handleSaveResultAsync(ParseObject.State result,
      ParseOperationSet operationsBeforeSave) {
    Task<Void> task = super.handleSaveResultAsync(result, operationsBeforeSave);

    if (result == null) { // Failure
      return task;
    }

    return task.onSuccessTask(new Continuation<Void, Task<Void>>() {
      @Override
      public Task<Void> then(Task<Void> task) throws Exception {
        return getCurrentInstallationController().setAsync(ParseInstallation.this);
      }
    });
  }

  @Override
  /* package */ Task<Void> handleFetchResultAsync(final ParseObject.State newState) {
    return super.handleFetchResultAsync(newState).onSuccessTask(new Continuation<Void, Task<Void>>() {
      @Override
      public Task<Void> then(Task<Void> task) throws Exception {
        return getCurrentInstallationController().setAsync(ParseInstallation.this);
      }
    });
  }

  // Android documentation states that getID may return one of many forms: America/LosAngeles,
  // GMT-<offset>, or code. We only accept the first on the server, so for now we will not upload
  // time zones from devices reporting other formats.
  private void updateTimezone() {
    String zone = TimeZone.getDefault().getID();
    if ((zone.indexOf('/') > 0 || zone.equals("GMT")) && !zone.equals(get("timeZone"))) {
      performPut("timeZone", zone);
    }
  }

  private void updateVersionInfo() {
    synchronized (mutex) {
      try {
        Context context = Parse.getApplicationContext();
        String packageName = context.getPackageName();
        PackageManager pm = context.getPackageManager();
        PackageInfo pkgInfo = pm.getPackageInfo(packageName, 0);
        String appVersion = pkgInfo.versionName;
        String appName = pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString();

        if (packageName != null && !packageName.equals(get("appIdentifier"))) {
          performPut(KEY_APP_IDENTIFIER, packageName);
        }
        if (appName != null && !appName.equals(get("appName"))) {
          performPut(KEY_APP_NAME, appName);
        }
        if (appVersion != null && !appVersion.equals(get("appVersion"))) {
          performPut(KEY_APP_VERSION, appVersion);
        }
      } catch (PackageManager.NameNotFoundException e) {
        PLog.w(TAG, "Cannot load package info; will not be saved to installation");
      }

      if (!VERSION_NAME.equals(get("parseVersion"))) {
        performPut(KEY_PARSE_VERSION, VERSION_NAME);
      }
    }
  }

  // TODO(mengyan): Move to ParseInstallationInstanceController
  /* package */ void updateDeviceInfo() {
    updateDeviceInfo(ParsePlugins.get().installationId());
  }

  /* package */ void updateDeviceInfo(InstallationId installationId) {
    /*
     * If we don't have an installationId, use the one that comes from the installationId file on
     * disk. This should be impossible since we set the installationId in setDefaultValues.
     */
    if (!has(KEY_INSTALLATION_ID)) {
      performPut(KEY_INSTALLATION_ID, installationId.get());
    }
    String deviceType = "android";
    if (!deviceType.equals(get(KEY_DEVICE_TYPE))) {
      performPut(KEY_DEVICE_TYPE, deviceType);
    }
  }

  /* package */ PushType getPushType() {
    return PushType.fromString(super.getString(KEY_PUSH_TYPE));
  }

  /* package */ void setPushType(PushType pushType) {
    if (pushType != null) {
      performPut(KEY_PUSH_TYPE, pushType.toString());
    }
  }

  /* package */ void removePushType() {
    performRemove(KEY_PUSH_TYPE);
  }

  /* package */ String getDeviceToken() {
    return super.getString(KEY_DEVICE_TOKEN);
  }

  /* package */ void setDeviceToken(String deviceToken) {
    if (deviceToken != null && deviceToken.length() > 0) {
      performPut(KEY_DEVICE_TOKEN, deviceToken);
    }
  }

  /* package */ void removeDeviceToken() {
    performRemove(KEY_DEVICE_TOKEN);
  }
}

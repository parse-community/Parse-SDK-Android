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
import android.text.TextUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import com.parse.boltsinternal.Continuation;
import com.parse.boltsinternal.Task;

/**
 * The {@code ParseInstallation} is a local representation of installation data that can be saved
 * and retrieved from the Parse cloud.
 */
@ParseClassName("_Installation")
public class ParseInstallation extends ParseObject {
    /* package */ static final String KEY_CHANNELS = "channels";
    private static final String TAG = "com.parse.ParseInstallation";
    private static final String KEY_OBJECT_ID = "objectId";
    private static final String KEY_INSTALLATION_ID = "installationId";
    private static final String KEY_DEVICE_TYPE = "deviceType";
    private static final String KEY_APP_NAME = "appName";
    private static final String KEY_APP_IDENTIFIER = "appIdentifier";
    private static final String KEY_PARSE_VERSION = "parseVersion";
    private static final String KEY_DEVICE_TOKEN = "deviceToken";
    private static final String KEY_PUSH_TYPE = "pushType";
    private static final String KEY_TIME_ZONE = "timeZone";
    private static final String KEY_LOCALE = "localeIdentifier";
    private static final String KEY_APP_VERSION = "appVersion";
    private static final List<String> READ_ONLY_FIELDS = Collections.unmodifiableList(
            Arrays.asList(KEY_DEVICE_TYPE, KEY_INSTALLATION_ID, KEY_DEVICE_TOKEN, KEY_PUSH_TYPE,
                    KEY_TIME_ZONE, KEY_LOCALE, KEY_APP_VERSION, KEY_APP_NAME, KEY_PARSE_VERSION,
                    KEY_APP_IDENTIFIER, KEY_OBJECT_ID));

    public ParseInstallation() {
        // do nothing
    }

    // TODO(mengyan): Inject into ParseInstallationInstanceController
    /* package */
    static ParseCurrentInstallationController getCurrentInstallationController() {
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

    /**
     * Returns the unique ID of this installation.
     *
     * @return A UUID that represents this device.
     */
    public String getInstallationId() {
        return getString(KEY_INSTALLATION_ID);
    }

    @Override
    public void setObjectId(String newObjectId) {
        throw new RuntimeException("Installation's objectId cannot be changed");
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
            updateLocaleIdentifier();
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
                public Task<T> then(Task<Void> task) {
                    return ParseInstallation.super.fetchAsync(sessionToken, toAwait);
                }
            });
        }
    }

    @Override
        /* package */ Task<Void> saveAsync(final String sessionToken, final Task<Void> toAwait) {
        return super.saveAsync(sessionToken, toAwait).continueWithTask(new Continuation<Void, Task<Void>>() {
            @Override
            public Task<Void> then(Task<Void> task) {
                // Retry the fetch as a save operation because this Installation was deleted on the server.
                if (task.getError() != null
                        && task.getError() instanceof ParseException) {
                    int errCode = ((ParseException) task.getError()).getCode();
                    if (errCode == ParseException.OBJECT_NOT_FOUND
                            || (errCode == ParseException.MISSING_REQUIRED_FIELD_ERROR && getObjectId() == null)) {
                        synchronized (mutex) {
                            setState(new State.Builder(getState()).objectId(null).build());
                            markAllFieldsDirty();
                            return ParseInstallation.super.saveAsync(sessionToken, toAwait);
                        }
                    }
                }
                return task;
            }
        });
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
            public Task<Void> then(Task<Void> task) {
                return getCurrentInstallationController().setAsync(ParseInstallation.this);
            }
        });
    }

    @Override
        /* package */ Task<Void> handleFetchResultAsync(final ParseObject.State newState) {
        return super.handleFetchResultAsync(newState).onSuccessTask(new Continuation<Void, Task<Void>>() {
            @Override
            public Task<Void> then(Task<Void> task) {
                return getCurrentInstallationController().setAsync(ParseInstallation.this);
            }
        });
    }

    // Android documentation states that getID may return one of many forms: America/LosAngeles,
    // GMT-<offset>, or code. We only accept the first on the server, so for now we will not upload
    // time zones from devices reporting other formats.
    private void updateTimezone() {
        String zone = TimeZone.getDefault().getID();
        if ((zone.indexOf('/') > 0 || zone.equals("GMT")) && !zone.equals(get(KEY_TIME_ZONE))) {
            performPut(KEY_TIME_ZONE, zone);
        }
    }

    private void updateVersionInfo() {
        synchronized (mutex) {
            try {
                Context context = Parse.getApplicationContext();
                String packageName = context.getPackageName();
                PackageManager pm = context.getPackageManager();
                PackageInfo pkgInfo = pm.getPackageInfo(packageName, 0);
                String appVersion = String.valueOf(pkgInfo.versionCode);
                String appName = pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString();

                if (packageName != null && !packageName.equals(get(KEY_APP_IDENTIFIER))) {
                    performPut(KEY_APP_IDENTIFIER, packageName);
                }
                if (appName != null && !appName.equals(get(KEY_APP_NAME))) {
                    performPut(KEY_APP_NAME, appName);
                }
                if (appVersion != null && !appVersion.equals(get(KEY_APP_VERSION))) {
                    performPut(KEY_APP_VERSION, appVersion);
                }
            } catch (PackageManager.NameNotFoundException e) {
                PLog.w(TAG, "Cannot load package info; will not be saved to installation");
            }
        }
    }

    /*
     * Save locale in the following format:
     *   [language code]-[country code]
     *
     * The language codes are two-letter lowercase ISO language codes (such as "en") as defined by
     * <a href="http://en.wikipedia.org/wiki/ISO_639-1">ISO 639-1</a>.
     * The country codes are two-letter uppercase ISO country codes (such as "US") as defined by
     * <a href="http://en.wikipedia.org/wiki/ISO_3166-1_alpha-3">ISO 3166-1</a>.
     *
     * Note that Java uses several deprecated two-letter codes. The Hebrew ("he") language
     * code is rewritten as "iw", Indonesian ("id") as "in", and Yiddish ("yi") as "ji". This
     * rewriting happens even if you construct your own {@code Locale} object, not just for
     * instances returned by the various lookup methods.
     */
    private void updateLocaleIdentifier() {
        final Locale locale = Locale.getDefault();

        String language = locale.getLanguage();
        String country = locale.getCountry();

        if (TextUtils.isEmpty(language)) {
            return;
        }

        // rewrite depreciated two-letter codes
        if (language.equals("iw")) language = "he"; // Hebrew
        if (language.equals("in")) language = "id"; // Indonesian
        if (language.equals("ji")) language = "yi"; // Yiddish

        String localeString = language;

        if (!TextUtils.isEmpty(country)) {
            localeString = String.format(Locale.US, "%s-%s", language, country);
        }

        if (!localeString.equals(get(KEY_LOCALE))) {
            performPut(KEY_LOCALE, localeString);
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

    public String getPushType() {
        return super.getString(KEY_PUSH_TYPE);
    }

    public void setPushType(String pushType) {
        if (pushType != null) {
            performPut(KEY_PUSH_TYPE, pushType);
        }
    }

    /* package */ void removePushType() {
        performRemove(KEY_PUSH_TYPE);
    }

    public String getDeviceToken() {
        return super.getString(KEY_DEVICE_TOKEN);
    }

    public void setDeviceToken(String deviceToken) {
        if (deviceToken != null && deviceToken.length() > 0) {
            performPut(KEY_DEVICE_TOKEN, deviceToken);
        }
    }

    /* package */ void removeDeviceToken() {
        performRemove(KEY_DEVICE_TOKEN);
    }
}

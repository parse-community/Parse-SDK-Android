/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse

import android.content.pm.PackageManager
import android.text.TextUtils
import com.parse.PLog.w
import kotlin.jvm.JvmOverloads
import com.parse.ParseTaskUtils
import com.parse.ParseQuery
import com.parse.boltsinternal.Task
import java.lang.RuntimeException
import java.util.*

/**
 * The `ParseInstallation` is a local representation of installation data that can be saved
 * and retrieved from the Parse cloud.
 */
@ParseClassName("_Installation")
class ParseInstallation : ParseObject() {
    /**
     * Returns the unique ID of this installation.
     *
     * @return A UUID that represents this device.
     */
    val installationId: String?
        get() = getString(KEY_INSTALLATION_ID)

    override fun  /* package */needsDefaultACL(): Boolean {
        return false
    }

    override fun  /* package */isKeyMutable(key: String?): Boolean {
        return !READ_ONLY_FIELDS.contains(key)
    }

    override fun  /* package */updateBeforeSave() {
        super.updateBeforeSave()
        if (currentInstallationController.isCurrent(this@ParseInstallation)) {
            updateTimezone()
            updateVersionInfo()
            updateDeviceInfo()
            updateLocaleIdentifier()
        }
    }

    override fun  /* package */<T : ParseObject?> fetchAsync(
        sessionToken: String?, toAwait: Task<Void?>
    ): Task<T>? {
        synchronized(mutex) {

            // Because the Service and the global currentInstallation are different objects, we may not
            // have the same ObjectID (we never will at bootstrap). The server has a special hack for
            // _Installation where save with an existing InstallationID will merge Object IDs
            val result: Task<Void?>? = if (objectId == null) {
                saveAsync(sessionToken, toAwait)
            } else {
                Task.forResult(null)
            }
            return result!!.onSuccessTask {
                super@ParseInstallation.fetchAsync(
                    sessionToken,
                    toAwait
                )
            }
        }
    }

    override fun saveAsync(
        sessionToken: String?,
        toAwait: Task<Void?>?
    ): Task<Void?>? {
        return super.saveAsync(sessionToken, toAwait)!!.continueWithTask { task: Task<Void?> ->
            // Retry the fetch as a save operation because this Installation was deleted on the server.
            if (task.error != null
                && task.error is ParseException
            ) {
                val errCode = (task.error as ParseException).code
                if (errCode == ParseException.OBJECT_NOT_FOUND
                    || errCode == ParseException.MISSING_REQUIRED_FIELD_ERROR && objectId == null
                ) {
                    synchronized(mutex) {
                        state = State.Builder(state).objectId(null).build()
                        markAllFieldsDirty()
                        return@continueWithTask super@ParseInstallation.saveAsync(
                            sessionToken,
                            toAwait
                        )
                    }
                }
            }
            task
        }
    }

    override fun  /* package */handleSaveResultAsync(
        result: State?,
        operationsBeforeSave: ParseOperationSet
    ): Task<Void?> {
        val task = super.handleSaveResultAsync(result, operationsBeforeSave)
        return if (result == null) { // Failure
            task
        } else task.onSuccessTask {
            currentInstallationController.setAsync(
                this@ParseInstallation
            )
        }
    }

    override fun handleFetchResultAsync(result: State): Task<Void?>? {
        return super.handleFetchResultAsync(result)!!
            .onSuccessTask { currentInstallationController.setAsync(this@ParseInstallation) }
    }

    // Android documentation states that getID may return one of many forms: America/LosAngeles,
    // GMT-<offset>, or code. We only accept the first on the server, so for now we will not upload
    // time zones from devices reporting other formats.
    private fun updateTimezone() {
        val zone = TimeZone.getDefault().id
        if ((zone.indexOf('/') > 0 || zone == "GMT") && zone != get(KEY_TIME_ZONE)) {
            performPut(KEY_TIME_ZONE, zone)
        }
    }

    private fun updateVersionInfo() {
        synchronized(mutex) {
            try {
                val context = Parse.getApplicationContext()
                val packageName = context.packageName
                val pm = context.packageManager
                val pkgInfo = pm.getPackageInfo(packageName!!, 0)
                val appVersion = pkgInfo.versionCode.toString()
                val appName =
                    pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
                if (packageName != null && packageName != get(KEY_APP_IDENTIFIER)) {
                    performPut(KEY_APP_IDENTIFIER, packageName)
                }
                if (appName != null && appName != get(KEY_APP_NAME)) {
                    performPut(KEY_APP_NAME, appName)
                }
                if (appVersion != null && appVersion != get(KEY_APP_VERSION)) {
                    performPut(KEY_APP_VERSION, appVersion)
                }
            } catch (e: PackageManager.NameNotFoundException) {
                w(TAG, "Cannot load package info; will not be saved to installation")
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
    private fun updateLocaleIdentifier() {
        val locale = Locale.getDefault()
        var language = locale.language
        val country = locale.country
        if (TextUtils.isEmpty(language)) {
            return
        }

        // rewrite depreciated two-letter codes
        if (language == "iw") language = "he" // Hebrew
        if (language == "in") language = "id" // Indonesian
        if (language == "ji") language = "yi" // Yiddish
        var localeString = language
        if (!TextUtils.isEmpty(country)) {
            localeString = String.format(Locale.US, "%s-%s", language, country)
        }
        if (localeString != get(KEY_LOCALE)) {
            performPut(KEY_LOCALE, localeString)
        }
    }

    /* package */ // TODO(mengyan): Move to ParseInstallationInstanceController
    /* package */
    @JvmOverloads
    fun updateDeviceInfo(installationId: InstallationId = ParsePlugins.get().installationId()) {
        /*
         * If we don't have an installationId, use the one that comes from the installationId file on
         * disk. This should be impossible since we set the installationId in setDefaultValues.
         */
        if (!has(KEY_INSTALLATION_ID)) {
            performPut(KEY_INSTALLATION_ID, installationId.get()!!)
        }
        val deviceType = "android"
        if (deviceType != get(KEY_DEVICE_TYPE)) {
            performPut(KEY_DEVICE_TYPE, deviceType)
        }
    }

    var pushType: String?
        get() = super.getString(KEY_PUSH_TYPE)
        set(pushType) {
            if (pushType != null) {
                performPut(KEY_PUSH_TYPE, pushType)
            }
        }

    /* package */
    fun removePushType() {
        performRemove(KEY_PUSH_TYPE)
    }

    var deviceToken: String?
        get() = super.getString(KEY_DEVICE_TOKEN)
        set(deviceToken) {
            if (deviceToken != null && deviceToken.isNotEmpty()) {
                performPut(KEY_DEVICE_TOKEN, deviceToken)
            }
        }

    /* package */
    fun removeDeviceToken() {
        performRemove(KEY_DEVICE_TOKEN)
    }

    companion object {
        /* package */
        const val KEY_CHANNELS = "channels"
        private const val TAG = "com.parse.ParseInstallation"
        private const val KEY_OBJECT_ID = "objectId"
        private const val KEY_INSTALLATION_ID = "installationId"
        private const val KEY_DEVICE_TYPE = "deviceType"
        private const val KEY_APP_NAME = "appName"
        private const val KEY_APP_IDENTIFIER = "appIdentifier"
        private const val KEY_PARSE_VERSION = "parseVersion"
        private const val KEY_DEVICE_TOKEN = "deviceToken"
        private const val KEY_PUSH_TYPE = "pushType"
        private const val KEY_TIME_ZONE = "timeZone"
        private const val KEY_LOCALE = "localeIdentifier"
        private const val KEY_APP_VERSION = "appVersion"
        private val READ_ONLY_FIELDS = Collections.unmodifiableList(
            listOf(
                KEY_DEVICE_TYPE, KEY_INSTALLATION_ID, KEY_DEVICE_TOKEN, KEY_PUSH_TYPE,
                KEY_TIME_ZONE, KEY_LOCALE, KEY_APP_VERSION, KEY_APP_NAME, KEY_PARSE_VERSION,
                KEY_APP_IDENTIFIER, KEY_OBJECT_ID
            )
        )

        // TODO(mengyan): Inject into ParseInstallationInstanceController
        /* package */
        @JvmStatic
        internal val currentInstallationController: ParseCurrentInstallationController
            get() = ParseCorePlugins.getInstance().currentInstallationController

        // In order to have backward compatibility, we swallow the exception silently.
        @JvmStatic
        val currentInstallation: ParseInstallation?
            get() = try {
                ParseTaskUtils.wait(
                    currentInstallationController.async
                )
            } catch (e: ParseException) {
                // In order to have backward compatibility, we swallow the exception silently.
                null
            }

        /**
         * Constructs a query for `ParseInstallation`.
         *
         *
         * **Note:** We only allow the following types of queries for installations:
         * <pre>
         * query.get(objectId)
         * query.whereEqualTo("installationId", value)
         * query.whereMatchesKeyInQuery("installationId", keyInQuery, query)
        </pre> *
         *
         *
         * You can add additional query clauses, but one of the above must appear as a top-level
         * `AND` clause in the query.
         *
         * @see com.parse.ParseQuery.getQuery
         */
        @JvmStatic
        val query: ParseQuery<ParseInstallation>
            get() = ParseQuery.getQuery(ParseInstallation::class.java)
    }
}
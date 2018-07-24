/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import android.content.Intent;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import bolts.Capture;
import bolts.Continuation;
import bolts.Task;

/**
 * The {@code ParseAnalytics} class provides an interface to Parse's logging and analytics backend.
 * Methods will return immediately and cache requests (+ timestamps) to be handled "eventually."
 * That is, the request will be sent immediately if possible or the next time a network connection
 * is available otherwise.
 */
public class ParseAnalytics {
    private static final String TAG = "com.parse.ParseAnalytics";
    // Developers have the option to manually track push opens or the app open event can be tracked
    // automatically by the ParsePushBroadcastReceiver. To avoid double-counting a push open, we track
    // the pushes we've seen locally. We don't need to worry about doing this in any sort of durable
    // way because a push can only launch the app once.
    private static final Map<String, Boolean> lruSeenPushes = new LinkedHashMap<String, Boolean>() {
        protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
            return size() > 10;
        }
    };

    /* package for test */
    static ParseAnalyticsController getAnalyticsController() {
        return ParseCorePlugins.getInstance().getAnalyticsController();
    }

    /**
     * Tracks this application being launched (and if this happened as the result of the user opening
     * a push notification, this method sends along information to correlate this open with that
     * push).
     *
     * @param intent The {@code Intent} that started an {@code Activity}, if any. Can be null.
     * @return A Task that is resolved when the event has been tracked by Parse.
     */
    public static Task<Void> trackAppOpenedInBackground(Intent intent) {
        String pushHashStr = getPushHashFromIntent(intent);
        final Capture<String> pushHash = new Capture<>();
        if (pushHashStr != null && pushHashStr.length() > 0) {
            synchronized (lruSeenPushes) {
                if (lruSeenPushes.containsKey(pushHashStr)) {
                    return Task.forResult(null);
                } else {
                    lruSeenPushes.put(pushHashStr, true);
                    pushHash.set(pushHashStr);
                }
            }
        }
        return ParseUser.getCurrentSessionTokenAsync().onSuccessTask(new Continuation<String, Task<Void>>() {
            @Override
            public Task<Void> then(Task<String> task) {
                String sessionToken = task.getResult();
                return getAnalyticsController().trackAppOpenedInBackground(pushHash.get(), sessionToken);
            }
        });
    }

    /**
     * Tracks this application being launched (and if this happened as the result of the user opening
     * a push notification, this method sends along information to correlate this open with that
     * push).
     *
     * @param intent   The {@code Intent} that started an {@code Activity}, if any. Can be null.
     * @param callback callback.done(e) is called when the event has been tracked by Parse.
     */
    public static void trackAppOpenedInBackground(Intent intent, SaveCallback callback) {
        ParseTaskUtils.callbackOnMainThreadAsync(trackAppOpenedInBackground(intent), callback);
    }

    /**
     * Tracks the occurrence of a custom event. Parse will store a data point at the time of
     * invocation with the given event name.
     *
     * @param name     The name of the custom event to report to Parse as having happened.
     * @param callback callback.done(e) is called when the event has been tracked by Parse.
     */
    public static void trackEventInBackground(String name, SaveCallback callback) {
        ParseTaskUtils.callbackOnMainThreadAsync(trackEventInBackground(name), callback);
    }

    /**
     * Tracks the occurrence of a custom event with additional dimensions. Parse will store a data
     * point at the time of invocation with the given event name.  Dimensions will allow segmentation
     * of the occurrences of this custom event.
     * <p>
     * To track a user signup along with additional metadata, consider the following:
     * <pre>
     * Map<String, String> dimensions = new HashMap<String, String>();
     * dimensions.put("gender", "m");
     * dimensions.put("source", "web");
     * dimensions.put("dayType", "weekend");
     * ParseAnalytics.trackEvent("signup", dimensions);
     * </pre>
     * There is a default limit of 8 dimensions per event tracked.
     *
     * @param name       The name of the custom event to report to Parse as having happened.
     * @param dimensions The dictionary of information by which to segment this event.
     * @param callback   callback.done(e) is called when the event has been tracked by Parse.
     */
    public static void trackEventInBackground(String name, Map<String, String> dimensions, SaveCallback callback) {
        ParseTaskUtils.callbackOnMainThreadAsync(trackEventInBackground(name, dimensions), callback);
    }

    /**
     * Tracks the occurrence of a custom event with additional dimensions. Parse will store a data
     * point at the time of invocation with the given event name.  Dimensions will allow segmentation
     * of the occurrences of this custom event.
     * <p>
     * To track a user signup along with additional metadata, consider the following:
     * <pre>
     * Map<String, String> dimensions = new HashMap<String, String>();
     * dimensions.put("gender", "m");
     * dimensions.put("source", "web");
     * dimensions.put("dayType", "weekend");
     * ParseAnalytics.trackEvent("signup", dimensions);
     * </pre>
     * There is a default limit of 8 dimensions per event tracked.
     *
     * @param name The name of the custom event to report to Parse as having happened.
     * @return A Task that is resolved when the event has been tracked by Parse.
     */
    public static Task<Void> trackEventInBackground(String name) {
        return trackEventInBackground(name, (Map<String, String>) null);
    }

    /**
     * Tracks the occurrence of a custom event with additional dimensions. Parse will store a data
     * point at the time of invocation with the given event name.  Dimensions will allow segmentation
     * of the occurrences of this custom event.
     * <p>
     * To track a user signup along with additional metadata, consider the following:
     * <pre>
     * Map<String, String> dimensions = new HashMap<String, String>();
     * dimensions.put("gender", "m");
     * dimensions.put("source", "web");
     * dimensions.put("dayType", "weekend");
     * ParseAnalytics.trackEvent("signup", dimensions);
     * </pre>
     * There is a default limit of 8 dimensions per event tracked.
     *
     * @param name       The name of the custom event to report to Parse as having happened.
     * @param dimensions The dictionary of information by which to segment this event.
     * @return A Task that is resolved when the event has been tracked by Parse.
     */
    public static Task<Void> trackEventInBackground(final String name,
                                                    Map<String, String> dimensions) {
        if (name == null || name.trim().length() == 0) {
            throw new IllegalArgumentException("A name for the custom event must be provided.");
        }
        final Map<String, String> dimensionsCopy = dimensions != null
                ? Collections.unmodifiableMap(new HashMap<>(dimensions))
                : null;

        return ParseUser.getCurrentSessionTokenAsync().onSuccessTask(new Continuation<String, Task<Void>>() {
            @Override
            public Task<Void> then(Task<String> task) {
                String sessionToken = task.getResult();
                return getAnalyticsController().trackEventInBackground(name, dimensionsCopy, sessionToken);
            }
        });
    }

    /* package */
    static void clear() {
        synchronized (lruSeenPushes) {
            lruSeenPushes.clear();
        }
    }

    /* package for test */
    static String getPushHashFromIntent(Intent intent) {
        String pushData = null;
        if (intent != null && intent.getExtras() != null) {
            pushData = intent.getExtras().getString(ParsePushBroadcastReceiver.KEY_PUSH_DATA);
        }
        if (pushData == null) {
            return null;
        }
        String pushHash = null;
        try {
            JSONObject payload = new JSONObject(pushData);
            pushHash = payload.optString("push_hash");
        } catch (JSONException e) {
            PLog.e(TAG, "Failed to parse push data: " + e.getMessage());
        }
        return pushHash;
    }
}

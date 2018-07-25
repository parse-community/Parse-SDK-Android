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
import android.os.Bundle;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Matchers;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import bolts.Task;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyMapOf;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// For android.os.BaseBundle
@RunWith(RobolectricTestRunner.class)
@Config(constants = BuildConfig.class, sdk = TestHelper.ROBOLECTRIC_SDK_VERSION)
public class ParseAnalyticsTest {

    ParseAnalyticsController controller;

    @Before
    public void setUp() {
        ParseTestUtils.setTestParseUser();

        // Mock ParseAnalyticsController
        controller = mock(ParseAnalyticsController.class);
        when(controller.trackEventInBackground(
                anyString(),
                anyMapOf(String.class, String.class),
                anyString())).thenReturn(Task.<Void>forResult(null));
        when(controller.trackAppOpenedInBackground(
                anyString(),
                anyString())).thenReturn(Task.<Void>forResult(null));

        ParseCorePlugins.getInstance().registerAnalyticsController(controller);
    }

    @After
    public void tearDown() {
        ParseCorePlugins.getInstance().reset();
        // Clear LRU cache in ParseAnalytics
        ParseAnalytics.clear();
        controller = null;
    }

    // No need to test ParseAnalytics since it has no instance fields and all methods are static.

    @Test
    public void testGetAnalyticsController() {
        assertSame(controller, ParseAnalytics.getAnalyticsController());
    }

    //region trackEventInBackground

    @Test(expected = IllegalArgumentException.class)
    public void testTrackEventInBackgroundNullName() throws Exception {
        ParseTaskUtils.wait(ParseAnalytics.trackEventInBackground(null));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testTrackEventInBackgroundEmptyName() throws Exception {
        ParseTaskUtils.wait(ParseAnalytics.trackEventInBackground(""));
    }

    @Test
    public void testTrackEventInBackgroundNormalName() throws Exception {
        ParseTaskUtils.wait(ParseAnalytics.trackEventInBackground("test"));

        verify(controller, times(1)).trackEventInBackground(
                eq("test"), Matchers.<Map<String, String>>eq(null), isNull(String.class));
    }

    @Test
    public void testTrackEventInBackgroundNullParameters() throws Exception {
        ParseTaskUtils.wait(ParseAnalytics.trackEventInBackground("test", (Map<String, String>) null));

        verify(controller, times(1)).trackEventInBackground(
                eq("test"), Matchers.<Map<String, String>>eq(null), isNull(String.class));
    }

    @Test
    public void testTrackEventInBackgroundEmptyParameters() throws Exception {
        Map<String, String> dimensions = new HashMap<>();
        ParseTaskUtils.wait(ParseAnalytics.trackEventInBackground("test", dimensions));

        verify(controller, times(1)).trackEventInBackground(
                eq("test"), eq(dimensions), isNull(String.class));
    }

    @Test
    public void testTrackEventInBackgroundNormalParameters() throws Exception {
        Map<String, String> dimensions = new HashMap<>();
        dimensions.put("key", "value");
        ParseTaskUtils.wait(ParseAnalytics.trackEventInBackground("test", dimensions));

        verify(controller, times(1)).trackEventInBackground(
                eq("test"), eq(dimensions), isNull(String.class));
    }

    @Test
    public void testTrackEventInBackgroundNullCallback() {
        Map<String, String> dimensions = new HashMap<>();
        ParseAnalytics.trackEventInBackground("test", dimensions, null);

        verify(controller, times(1)).trackEventInBackground(
                eq("test"), eq(dimensions), isNull(String.class));
    }


    @Test
    public void testTrackEventInBackgroundNormalCallback() throws Exception {
        final Map<String, String> dimensions = new HashMap<>();
        dimensions.put("key", "value");
        final Semaphore done = new Semaphore(0);
        ParseAnalytics.trackEventInBackground("test", dimensions,
                new SaveCallback() {
                    @Override
                    public void done(ParseException e) {
                        assertNull(e);
                        done.release();
                    }
                });

        // Make sure the callback is called
        assertTrue(done.tryAcquire(1, 10, TimeUnit.SECONDS));
        verify(controller, times(1)).trackEventInBackground(
                eq("test"), eq(dimensions), isNull(String.class));

        final Semaphore doneAgain = new Semaphore(0);
        ParseAnalytics.trackEventInBackground("test", new SaveCallback() {
            @Override
            public void done(ParseException e) {
                assertNull(e);
                doneAgain.release();
            }
        });

        // Make sure the callback is called
        assertTrue(doneAgain.tryAcquire(1, 10, TimeUnit.SECONDS));
        verify(controller, times(1)).trackEventInBackground
                (eq("test"), Matchers.<Map<String, String>>eq(null), isNull(String.class));
    }

    //endregion

    //region testTrackAppOpenedInBackground

    @Test
    public void testTrackAppOpenedInBackgroundNullIntent() throws Exception {
        ParseTaskUtils.wait(ParseAnalytics.trackAppOpenedInBackground(null));

        verify(controller, times(1)).trackAppOpenedInBackground(isNull(String.class),
                isNull(String.class));
    }

    @Test
    public void testTrackAppOpenedInBackgroundEmptyIntent() throws Exception {
        Intent intent = new Intent();
        ParseTaskUtils.wait(ParseAnalytics.trackAppOpenedInBackground(intent));

        verify(controller, times(1)).trackAppOpenedInBackground(isNull(String.class),
                isNull(String.class));
    }

    @Test
    public void testTrackAppOpenedInBackgroundNormalIntent() throws Exception {
        Intent intent = makeIntentWithParseData("test");
        ParseTaskUtils.wait(ParseAnalytics.trackAppOpenedInBackground(intent));

        verify(controller, times(1)).trackAppOpenedInBackground(eq("test"), isNull(String.class));
    }

    @Test
    public void testTrackAppOpenedInBackgroundDuplicatedIntent() throws Exception {
        Intent intent = makeIntentWithParseData("test");
        ParseTaskUtils.wait(ParseAnalytics.trackAppOpenedInBackground(intent));

        verify(controller, times(1)).trackAppOpenedInBackground(eq("test"), isNull(String.class));

        ParseTaskUtils.wait(ParseAnalytics.trackAppOpenedInBackground(intent));

        verify(controller, times(1)).trackAppOpenedInBackground(eq("test"), isNull(String.class));
    }

    @Test
    public void testTrackAppOpenedInBackgroundNullCallback() throws Exception {
        Intent intent = makeIntentWithParseData("test");
        ParseAnalytics.trackAppOpenedInBackground(intent, null);

        verify(controller, times(1)).trackAppOpenedInBackground(eq("test"), isNull(String.class));
    }

    @Test
    public void testTrackAppOpenedInBackgroundNormalCallback() throws Exception {
        Intent intent = makeIntentWithParseData("test");
        final Semaphore done = new Semaphore(0);
        ParseAnalytics.trackAppOpenedInBackground(intent, new SaveCallback() {
            @Override
            public void done(ParseException e) {
                assertNull(e);
                done.release();
            }
        });

        // Make sure the callback is called
        assertTrue(done.tryAcquire(1, 10, TimeUnit.SECONDS));
        verify(controller, times(1)).trackAppOpenedInBackground(eq("test"), isNull(String.class));
    }

    //endregion

    //region testGetPushHashFromIntent

    @Test
    public void testGetPushHashFromIntentNullIntent() {
        String pushHash = ParseAnalytics.getPushHashFromIntent(null);

        assertEquals(null, pushHash);
    }

    @Test
    public void testGetPushHashFromIntentEmptyIntent() throws Exception {
        Intent intent = new Intent();
        Bundle bundle = new Bundle();
        JSONObject json = new JSONObject();
        json.put("push_hash_wrong_key", "test");
        bundle.putString("data_wrong_key", json.toString());
        intent.putExtras(bundle);

        String pushHash = ParseAnalytics.getPushHashFromIntent(intent);

        assertEquals(null, pushHash);
    }

    @Test
    public void testGetPushHashFromIntentEmptyPushHashIntent() throws Exception {
        Intent intent = new Intent();
        Bundle bundle = new Bundle();
        JSONObject json = new JSONObject();
        json.put("push_hash_wrong_key", "test");
        bundle.putString(ParsePushBroadcastReceiver.KEY_PUSH_DATA, json.toString());
        intent.putExtras(bundle);

        String pushHash = ParseAnalytics.getPushHashFromIntent(intent);

        assertEquals("", pushHash);
    }

    @Test
    public void testGetPushHashFromIntentWrongPushHashIntent() {
        Intent intent = new Intent();
        Bundle bundle = new Bundle();
        bundle.putString(ParsePushBroadcastReceiver.KEY_PUSH_DATA, "error_data");
        intent.putExtras(bundle);

        String pushHash = ParseAnalytics.getPushHashFromIntent(intent);

        assertEquals(null, pushHash);
    }

    @Test
    public void testGetPushHashFromIntentNormalIntent() throws Exception {
        Intent intent = makeIntentWithParseData("test");

        String pushHash = ParseAnalytics.getPushHashFromIntent(intent);

        assertEquals("test", pushHash);
    }

    //endregion

    private Intent makeIntentWithParseData(String pushHash) throws JSONException {
        Intent intent = new Intent();
        Bundle bundle = new Bundle();
        JSONObject json = new JSONObject();
        json.put("push_hash", pushHash);
        bundle.putString(ParsePushBroadcastReceiver.KEY_PUSH_DATA, json.toString());
        intent.putExtras(bundle);
        return intent;
    }
}

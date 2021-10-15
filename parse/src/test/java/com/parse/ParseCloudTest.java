/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Matchers.anyMap;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.robolectric.annotation.LooperMode.Mode.PAUSED;
import static org.robolectric.shadows.ShadowLooper.shadowMainLooper;

import com.parse.boltsinternal.Task;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.LooperMode;

// For android.os.Looper
@RunWith(RobolectricTestRunner.class)
@LooperMode(PAUSED)
public class ParseCloudTest extends ResetPluginsParseTest {

    @Before
    public void setUp() throws Exception {
        super.setUp();
        ParseTestUtils.setTestParseUser();
    }

    // region testGetCloudCodeController

    @Test
    public void testGetCloudCodeController() {
        ParseCloudCodeController controller = mock(ParseCloudCodeController.class);
        ParseCorePlugins.getInstance().registerCloudCodeController(controller);

        assertSame(controller, ParseCloud.getCloudCodeController());
    }

    // endregion

    // region testCallFunctions

    @Test
    public void testCallFunctionAsync() throws Exception {
        ParseCloudCodeController controller = mockParseCloudCodeControllerWithResponse("result");
        ParseCorePlugins.getInstance().registerCloudCodeController(controller);
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("key1", Arrays.asList(1, 2, 3));
        parameters.put("key2", "value1");

        Task cloudCodeTask = ParseCloud.callFunctionInBackground("name", parameters);
        ParseTaskUtils.wait(cloudCodeTask);

        verify(controller, times(1)).callFunctionInBackground(eq("name"), eq(parameters), isNull());
        assertTrue(cloudCodeTask.isCompleted());
        assertNull(cloudCodeTask.getError());
        assertThat(cloudCodeTask.getResult(), instanceOf(String.class));
        assertEquals("result", cloudCodeTask.getResult());
    }

    @Test
    public void testCallFunctionSync() throws Exception {
        ParseCloudCodeController controller = mockParseCloudCodeControllerWithResponse("result");
        ParseCorePlugins.getInstance().registerCloudCodeController(controller);
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("key1", Arrays.asList(1, 2, 3));
        parameters.put("key2", "value1");

        Object result = ParseCloud.callFunction("name", parameters);

        verify(controller, times(1)).callFunctionInBackground(eq("name"), eq(parameters), isNull());
        assertThat(result, instanceOf(String.class));
        assertEquals("result", result);
    }

    @Test
    public void testCallFunctionNullCallback() {
        ParseCloudCodeController controller = mockParseCloudCodeControllerWithResponse("result");
        ParseCorePlugins.getInstance().registerCloudCodeController(controller);
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("key1", Arrays.asList(1, 2, 3));
        parameters.put("key2", "value1");

        ParseCloud.callFunctionInBackground("name", parameters, null);

        verify(controller, times(1)).callFunctionInBackground(eq("name"), eq(parameters), isNull());
    }

    @Test
    public void testCallFunctionNormalCallback() throws Exception {
        ParseCloudCodeController controller = mockParseCloudCodeControllerWithResponse("result");
        ParseCorePlugins.getInstance().registerCloudCodeController(controller);
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("key1", Arrays.asList(1, 2, 3));
        parameters.put("key2", "value1");

        final Semaphore done = new Semaphore(0);
        ParseCloud.callFunctionInBackground(
                "name",
                parameters,
                (result, e) -> {
                    assertNull(e);
                    assertThat(result, instanceOf(String.class));
                    assertEquals("result", result);
                    done.release();
                });

        shadowMainLooper().idle();

        // Make sure the callback is called
        assertTrue(done.tryAcquire(1, 10, TimeUnit.SECONDS));
        verify(controller, times(1)).callFunctionInBackground(eq("name"), eq(parameters), isNull());
    }

    // endregion

    private <T> ParseCloudCodeController mockParseCloudCodeControllerWithResponse(final T result) {
        ParseCloudCodeController controller = mock(ParseCloudCodeController.class);
        when(controller.callFunctionInBackground(anyString(), anyMap(), nullable(String.class)))
                .thenReturn(Task.forResult(result));
        return controller;
    }
}

/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.annotation.Config;

import bolts.Task;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// For android.net.Uri
@RunWith(RobolectricGradleTestRunner.class)
@Config(constants = BuildConfig.class, sdk = 21)
public class ParseAnalyticsControllerTest {

  //region testConstructor

  @Test
  public void testConstructor() {
    ParseEventuallyQueue queue = mock(ParseEventuallyQueue.class);
    ParseAnalyticsController controller = new ParseAnalyticsController(queue);
    assertSame(queue, controller.eventuallyQueue);
  }

  //endregion

  //region trackEventInBackground

  @Test
  public void testTrackEvent() throws Exception {
    // Mock eventually queue
    ParseEventuallyQueue queue = mock(ParseEventuallyQueue.class);
    when(queue.enqueueEventuallyAsync(any(ParseRESTCommand.class), any(ParseObject.class)))
        .thenReturn(Task.forResult(new JSONObject()));

    // Execute
    ParseAnalyticsController controller = new ParseAnalyticsController(queue);
    JSONObject json = new JSONObject();
    json.put("event", "close");
    ParseTaskUtils.wait(controller.trackEventInBackground("name", json, "sessionToken"));

    // Verify eventuallyQueue.enqueueEventuallyAsync
    ArgumentCaptor<ParseRESTCommand> command = ArgumentCaptor.forClass(ParseRESTCommand.class);
    ArgumentCaptor<ParseObject> object = ArgumentCaptor.forClass(ParseObject.class);
    verify(queue, times(1)).enqueueEventuallyAsync(command.capture(), object.capture());

    // Verify eventuallyQueue.enqueueEventuallyAsync object parameter
    assertNull(object.getValue());

    // Verify eventuallyQueue.enqueueEventuallyAsync command parameter
    assertTrue(command.getValue() instanceof ParseRESTAnalyticsCommand);
    assertTrue(command.getValue().httpPath.contains("name"));
    assertEquals("sessionToken", command.getValue().getSessionToken());
    JSONObject jsonDimensions = command.getValue().jsonParameters.getJSONObject("dimensions");
    assertEquals("close", jsonDimensions.get("event"));
    assertEquals(1, jsonDimensions.length());
  }

  //endregion

  //region trackAppOpenedInBackground

  @Test
  public void testTrackAppOpened() throws Exception {
    // Mock eventually queue
    ParseEventuallyQueue queue = mock(ParseEventuallyQueue.class);
    when(queue.enqueueEventuallyAsync(any(ParseRESTCommand.class), any(ParseObject.class)))
        .thenReturn(Task.forResult(new JSONObject()));

    // Execute
    ParseAnalyticsController controller = new ParseAnalyticsController(queue);
    ParseTaskUtils.wait(controller.trackAppOpenedInBackground("pushHash", "sessionToken"));

    // Verify eventuallyQueue.enqueueEventuallyAsync
    ArgumentCaptor<ParseRESTCommand> command = ArgumentCaptor.forClass(ParseRESTCommand.class);
    ArgumentCaptor<ParseObject> object = ArgumentCaptor.forClass(ParseObject.class);
    verify(queue, times(1)).enqueueEventuallyAsync(command.capture(), object.capture());

    // Verify eventuallyQueue.enqueueEventuallyAsync object parameter
    assertNull(object.getValue());


    // Verify eventuallyQueue.enqueueEventuallyAsync command parameter
    assertTrue(command.getValue() instanceof ParseRESTAnalyticsCommand);
    assertTrue(command.getValue().httpPath.contains(ParseRESTAnalyticsCommand.EVENT_APP_OPENED));
    assertEquals("sessionToken", command.getValue().getSessionToken());
    assertEquals("pushHash", command.getValue().jsonParameters.get("push_hash"));
  }

  //endregion
}

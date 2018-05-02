/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import com.parse.http.ParseHttpRequest;
import com.parse.http.ParseHttpResponse;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.skyscreamer.jsonassert.JSONCompareMode;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import bolts.Task;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.skyscreamer.jsonassert.JSONAssert.assertEquals;

// For SSLSessionCache
@RunWith(RobolectricTestRunner.class)
@Config(constants = BuildConfig.class, sdk = TestHelper.ROBOLECTRIC_SDK_VERSION)
public class ParsePushControllerTest {

  @Before
  public void setUp() throws MalformedURLException {
    ParseRESTCommand.server = new URL("https://api.parse.com/1");
  }

  @After
  public void tearDown() {
    ParseRESTCommand.server = null;
  }

  //region testBuildRESTSendPushCommand

  @Test
  public void testBuildRESTSendPushCommandWithChannelSet() throws Exception {
    ParseHttpClient restClient = mock(ParseHttpClient.class);
    ParsePushController controller = new ParsePushController(restClient);

    // Build PushState
    JSONObject data = new JSONObject();
    data.put(ParsePush.KEY_DATA_MESSAGE, "hello world");
    List<String> expectedChannelSet = new ArrayList<String>(){{
      add("foo");
      add("bar");
      add("yarr");
    }};
    ParsePush.State state = new ParsePush.State.Builder()
        .data(data)
        .channelSet(expectedChannelSet)
        .build();

    // Build command
    ParseRESTCommand pushCommand = controller.buildRESTSendPushCommand(state, "sessionToken");

    // Verify command
    JSONObject jsonParameters = pushCommand.jsonParameters;
    assertFalse(jsonParameters.has(ParseRESTPushCommand.KEY_PUSH_TIME));
    assertFalse(jsonParameters.has(ParseRESTPushCommand.KEY_EXPIRATION_TIME));
    assertFalse(jsonParameters.has(ParseRESTPushCommand.KEY_EXPIRATION_INTERVAL));
    // Verify device type and query
    assertFalse(jsonParameters.has(ParseRESTPushCommand.KEY_WHERE));
    assertEquals("hello world",
        jsonParameters.getJSONObject(ParseRESTPushCommand.KEY_DATA)
            .getString(ParsePush.KEY_DATA_MESSAGE));
    JSONArray pushChannels = jsonParameters.getJSONArray(ParseRESTPushCommand.KEY_CHANNELS);
    assertEquals(new JSONArray(expectedChannelSet), pushChannels,
        JSONCompareMode.NON_EXTENSIBLE);
  }

  @Test
  public void testBuildRESTSendPushCommandWithExpirationTime() throws Exception {
    ParseHttpClient restClient = mock(ParseHttpClient.class);
    ParsePushController controller = new ParsePushController(restClient);

    // Build PushState
    JSONObject data = new JSONObject();
    data.put(ParsePush.KEY_DATA_MESSAGE, "hello world");
    ParsePush.State state = new ParsePush.State.Builder()
        .data(data)
        .expirationTime((long) 1400000000)
        .build();

    // Build command
    ParseRESTCommand pushCommand = controller.buildRESTSendPushCommand(state, "sessionToken");

    // Verify command
    JSONObject jsonParameters = pushCommand.jsonParameters;
    assertFalse(jsonParameters.has(ParseRESTPushCommand.KEY_PUSH_TIME));
    assertFalse(jsonParameters.has(ParseRESTPushCommand.KEY_EXPIRATION_INTERVAL));
    assertFalse(jsonParameters.has(ParseRESTPushCommand.KEY_CHANNELS));
    // Verify device type and query
    assertEquals("{}", jsonParameters.get(ParseRESTPushCommand.KEY_WHERE).toString());
    assertEquals("hello world",
        jsonParameters.getJSONObject(ParseRESTPushCommand.KEY_DATA)
            .getString(ParsePush.KEY_DATA_MESSAGE));
    assertEquals(1400000000, jsonParameters.getLong(ParseRESTPushCommand.KEY_EXPIRATION_TIME));
  }

  @Test
  public void testBuildRESTSendPushCommandWithPushTime() throws Exception {
    ParseHttpClient restClient = mock(ParseHttpClient.class);
    ParsePushController controller = new ParsePushController(restClient);

    // Build PushState
    JSONObject data = new JSONObject();
    data.put(ParsePush.KEY_DATA_MESSAGE, "hello world");
    long pushTime = System.currentTimeMillis() / 1000 + 1000;
    ParsePush.State state = new ParsePush.State.Builder()
        .data(data)
        .pushTime(pushTime)
        .build();

    // Build command
    ParseRESTCommand pushCommand = controller.buildRESTSendPushCommand(state, "sessionToken");

    // Verify command
    JSONObject jsonParameters = pushCommand.jsonParameters;
    assertFalse(jsonParameters.has(ParseRESTPushCommand.KEY_EXPIRATION_TIME));
    assertFalse(jsonParameters.has(ParseRESTPushCommand.KEY_EXPIRATION_INTERVAL));
    assertFalse(jsonParameters.has(ParseRESTPushCommand.KEY_CHANNELS));
    // Verify device type and query
    assertEquals("{}", jsonParameters.get(ParseRESTPushCommand.KEY_WHERE).toString());
    assertEquals("hello world",
        jsonParameters.getJSONObject(ParseRESTPushCommand.KEY_DATA)
            .getString(ParsePush.KEY_DATA_MESSAGE));
    assertEquals(pushTime, jsonParameters.getLong(ParseRESTPushCommand.KEY_PUSH_TIME));
  }

  @Test
  public void testBuildRESTSendPushCommandWithExpirationTimeInterval() throws Exception {
    ParseHttpClient restClient = mock(ParseHttpClient.class);
    ParsePushController controller = new ParsePushController(restClient);

    // Build PushState
    JSONObject data = new JSONObject();
    data.put(ParsePush.KEY_DATA_MESSAGE, "hello world");
    ParsePush.State state = new ParsePush.State.Builder()
        .data(data)
        .expirationTimeInterval((long) 86400)
        .build();

    // Build command
    ParseRESTCommand pushCommand = controller.buildRESTSendPushCommand(state, "sessionToken");

    // Verify command
    JSONObject jsonParameters = pushCommand.jsonParameters;
    assertFalse(jsonParameters.has(ParseRESTPushCommand.KEY_PUSH_TIME));
    assertFalse(jsonParameters.has(ParseRESTPushCommand.KEY_EXPIRATION_TIME));
    assertFalse(jsonParameters.has(ParseRESTPushCommand.KEY_CHANNELS));
    // Verify device type and query
    assertEquals("{}", jsonParameters.get(ParseRESTPushCommand.KEY_WHERE).toString());
    assertEquals("hello world",
        jsonParameters.getJSONObject(ParseRESTPushCommand.KEY_DATA)
            .getString(ParsePush.KEY_DATA_MESSAGE));
    assertEquals(86400, jsonParameters.getLong(ParseRESTPushCommand.KEY_EXPIRATION_INTERVAL));
  }

  @Test
  public void testBuildRESTSendPushCommandWithQuery() throws Exception {
    ParseHttpClient restClient = mock(ParseHttpClient.class);
    ParsePushController controller = new ParsePushController(restClient);

    // Build PushState
    JSONObject data = new JSONObject();
    data.put(ParsePush.KEY_DATA_MESSAGE, "hello world");
    ParseQuery<ParseInstallation> query = ParseInstallation.getQuery();
    query.whereEqualTo("language", "en/US");
    query.whereLessThan("version", "1.2");
    ParsePush.State state = new ParsePush.State.Builder()
        .data(data)
        .query(query)
        .build();

    // Build command
    ParseRESTCommand pushCommand = controller.buildRESTSendPushCommand(state, "sessionToken");

    // Verify command
    JSONObject jsonParameters = pushCommand.jsonParameters;
    assertFalse(jsonParameters.has(ParseRESTPushCommand.KEY_PUSH_TIME));
    assertFalse(jsonParameters.has(ParseRESTPushCommand.KEY_EXPIRATION_TIME));
    assertFalse(jsonParameters.has(ParseRESTPushCommand.KEY_EXPIRATION_INTERVAL));
    assertFalse(jsonParameters.getJSONObject(ParseRESTPushCommand.KEY_WHERE)
        .has(ParseRESTPushCommand.KEY_DEVICE_TYPE));
    assertFalse(jsonParameters.has(ParseRESTPushCommand.KEY_CHANNELS));
    assertEquals("hello world",
        jsonParameters
            .getJSONObject(ParseRESTPushCommand.KEY_DATA)
            .getString(ParsePush.KEY_DATA_MESSAGE));

    JSONObject pushQuery = jsonParameters.getJSONObject(ParseRESTPushCommand.KEY_WHERE);
    assertEquals("en/US", pushQuery.getString("language"));
    JSONObject inequality = pushQuery.getJSONObject("version");
    assertEquals("1.2", inequality.getString("$lt"));
  }

  @Test
  public void testBuildRESTSendPushCommandWithPushToAndroid() throws Exception {
    ParseHttpClient restClient = mock(ParseHttpClient.class);
    ParsePushController controller = new ParsePushController(restClient);

    // Build PushState
    JSONObject data = new JSONObject();
    data.put(ParsePush.KEY_DATA_MESSAGE, "hello world");
    ParsePush.State state = new ParsePush.State.Builder()
        .pushToAndroid(true)
        .data(data)
        .build();

    // Build command
    ParseRESTCommand pushCommand = controller.buildRESTSendPushCommand(state, "sessionToken");

    // Verify command
    JSONObject jsonParameters = pushCommand.jsonParameters;
    assertFalse(jsonParameters.has(ParseRESTPushCommand.KEY_PUSH_TIME));
    assertFalse(jsonParameters.has(ParseRESTPushCommand.KEY_EXPIRATION_TIME));
    assertFalse(jsonParameters.has(ParseRESTPushCommand.KEY_EXPIRATION_INTERVAL));
    assertFalse(jsonParameters.has(ParseRESTPushCommand.KEY_CHANNELS));
    assertEquals("hello world",
        jsonParameters.getJSONObject(ParseRESTPushCommand.KEY_DATA)
            .getString(ParsePush.KEY_DATA_MESSAGE));
    assertEquals(ParsePushController.DEVICE_TYPE_ANDROID,
        jsonParameters.getJSONObject(ParseRESTPushCommand.KEY_WHERE)
            .optString(ParseRESTPushCommand.KEY_DEVICE_TYPE, null));
  }

  @Test
  public void testBuildRESTSendPushCommandWithPushToIOS() throws Exception {
    ParseHttpClient restClient = mock(ParseHttpClient.class);
    ParsePushController controller = new ParsePushController(restClient);

    // Build PushState
    JSONObject data = new JSONObject();
    data.put(ParsePush.KEY_DATA_MESSAGE, "hello world");
    ParsePush.State state = new ParsePush.State.Builder()
        .pushToIOS(true)
        .data(data)
        .build();

    // Build command
    ParseRESTCommand pushCommand = controller.buildRESTSendPushCommand(state, "sessionToken");

    // Verify command
    JSONObject jsonParameters = pushCommand.jsonParameters;
    assertFalse(jsonParameters.has(ParseRESTPushCommand.KEY_PUSH_TIME));
    assertFalse(jsonParameters.has(ParseRESTPushCommand.KEY_EXPIRATION_TIME));
    assertFalse(jsonParameters.has(ParseRESTPushCommand.KEY_EXPIRATION_INTERVAL));
    assertFalse(jsonParameters.has(ParseRESTPushCommand.KEY_CHANNELS));
    assertEquals("hello world",
        jsonParameters.getJSONObject(ParseRESTPushCommand.KEY_DATA)
            .getString(ParsePush.KEY_DATA_MESSAGE));
    assertEquals(ParsePushController.DEVICE_TYPE_IOS,
        jsonParameters.getJSONObject(ParseRESTPushCommand.KEY_WHERE)
            .optString(ParseRESTPushCommand.KEY_DEVICE_TYPE, null));
  }

  @Test
  public void testBuildRESTSendPushCommandWithPushToIOSAndAndroid() throws Exception {
    ParseHttpClient restClient = mock(ParseHttpClient.class);
    ParsePushController controller = new ParsePushController(restClient);

    // Build PushState
    JSONObject data = new JSONObject();
    data.put(ParsePush.KEY_DATA_MESSAGE, "hello world");
    ParsePush.State state = new ParsePush.State.Builder()
        .pushToAndroid(true)
        .pushToIOS(true)
        .data(data)
        .build();

    // Build command
    ParseRESTCommand pushCommand = controller.buildRESTSendPushCommand(state, "sessionToken");

    // Verify command
    JSONObject jsonParameters = pushCommand.jsonParameters;
    assertFalse(jsonParameters.has(ParseRESTPushCommand.KEY_PUSH_TIME));
    assertFalse(jsonParameters.has(ParseRESTPushCommand.KEY_EXPIRATION_TIME));
    assertFalse(jsonParameters.has(ParseRESTPushCommand.KEY_EXPIRATION_INTERVAL));
    assertFalse(jsonParameters.has(ParseRESTPushCommand.KEY_CHANNELS));
    assertEquals("hello world",
        jsonParameters.getJSONObject(ParseRESTPushCommand.KEY_DATA)
            .getString(ParsePush.KEY_DATA_MESSAGE));
    assertFalse(jsonParameters.getJSONObject(ParseRESTPushCommand.KEY_WHERE)
        .has(ParseRESTPushCommand.KEY_DEVICE_TYPE));
  }

  //endregion

  //region testSendInBackground

  @Test
  public void testSendInBackgroundSuccess() throws Exception {
    JSONObject mockResponse = new JSONObject();
    mockResponse.put("result", "OK");
    ParseHttpClient restClient = mockParseHttpClientWithResponse(mockResponse, 200, "OK");
    ParsePushController controller = new ParsePushController(restClient);

    JSONObject data = new JSONObject();
    data.put(ParsePush.KEY_DATA_MESSAGE, "hello world");
    ParsePush.State state = new ParsePush.State.Builder()
      .data(data)
      .build();

    Task<Void> pushTask= controller.sendInBackground(state, "sessionToken");

    // Verify task complete
    ParseTaskUtils.wait(pushTask);
    // Verify httpclient execute encough times
    verify(restClient, times(1)).execute(any(ParseHttpRequest.class));
  }

  @Test
  public void testSendInBackgroundFailWithIOException() throws Exception {
    // TODO(mengyan): Remove once we no longer rely on retry logic.
    ParseRequest.setDefaultInitialRetryDelay(1L);

    ParseHttpClient restClient = mock(ParseHttpClient.class);
    when(restClient.execute(any(ParseHttpRequest.class))).thenThrow(new IOException());
    ParsePushController controller = new ParsePushController(restClient);

    JSONObject data = new JSONObject();
    data.put(ParsePush.KEY_DATA_MESSAGE, "hello world");
    ParsePush.State state = new ParsePush.State.Builder()
      .data(data)
      .build();

    Task<Void> pushTask= controller.sendInBackground(state, "sessionToken");

    // Do not use ParseTaskUtils.wait() since we do not want to throw the exception
    pushTask.waitForCompletion();
    // Verify httpClient is tried enough times
    // TODO(mengyan): Abstract out command runner so we don't have to account for retries.
    verify(restClient, times(5)).execute(any(ParseHttpRequest.class));
    assertTrue(pushTask.isFaulted());
    Exception error = pushTask.getError();
    assertThat(error, instanceOf(ParseException.class));
    assertEquals(ParseException.CONNECTION_FAILED, ((ParseException) error).getCode());
  }

  //endregion

  private ParseHttpClient mockParseHttpClientWithResponse(JSONObject content, int statusCode,
      String reasonPhrase) throws IOException {
    byte[] contentBytes = content.toString().getBytes();
    ParseHttpResponse response = new ParseHttpResponse.Builder()
        .setContent(new ByteArrayInputStream(contentBytes))
        .setStatusCode(statusCode)
        .setTotalSize(contentBytes.length)
        .setContentType("application/json")
        .build();
    ParseHttpClient client = mock(ParseHttpClient.class);
    when(client.execute(any(ParseHttpRequest.class))).thenReturn(response);
    return client;
  }

  private static boolean containsString(JSONArray array, String value) throws JSONException {
    for (int i = 0; i < array.length(); i++) {
      Object element = array.get(i);
      if (element instanceof String && element.equals(value)) {
        return true;
      }
    }
    return false;
  }
}

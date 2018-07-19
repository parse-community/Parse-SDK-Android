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

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import bolts.Task;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ParseConfigControllerTest {

  @Before
  public void setUp() throws MalformedURLException {
    ParseRESTCommand.server = new URL("https://api.parse.com/1");
  }

  @After
  public void tearDown() {
    ParseRESTCommand.server = null;
  }

  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  //region testConstructor

  @Test
  public void testConstructor() throws Exception {
    ParseHttpClient restClient = mock(ParseHttpClient.class);
    ParseCurrentConfigController currentConfigController = mock(ParseCurrentConfigController.class);
    ParseConfigController controller = new ParseConfigController(restClient,
        currentConfigController);

    assertSame(currentConfigController, controller.getCurrentConfigController());
  }

  //endregion

  //region testGetAsync

  @Test
  public void testGetAsyncSuccess() throws Exception {
    // Construct sample response from server
    final Date date = new Date();
    final ParseFile file = new ParseFile(
        new ParseFile.State.Builder().name("image.png").url("http://yarr.com/image.png").build());
    final ParseGeoPoint geoPoint = new ParseGeoPoint(44.484, 26.029);
    final List<Object> list = new ArrayList<Object>() {{
      add("foo");
      add("bar");
      add("baz");
    }};
    final Map<String, Object> map = new HashMap<String, Object>() {{
      put("first", "foo");
      put("second", "bar");
      put("third", "baz");
    }};

    final Map<String, Object> sampleConfigParameters = new HashMap<String, Object>() {{
      put("string", "value");
      put("int", 42);
      put("double", 0.2778);
      put("trueBool", true);
      put("falseBool", false);
      put("date", date);
      put("file", file);
      put("geoPoint", geoPoint);
      put("array", list);
      put("object", map);
    }};

    JSONObject responseJson = new JSONObject() {{
      put("params", NoObjectsEncoder.get().encode(sampleConfigParameters));
    }};

    // Make ParseConfigController and call getAsync
    ParseHttpClient restClient = mockParseHttpClientWithResponse(responseJson, 200, "OK");
    ParseCurrentConfigController currentConfigController = mockParseCurrentConfigController();
    ParseConfigController configController =
        new ParseConfigController(restClient, currentConfigController);

    Task<ParseConfig> configTask = configController.getAsync(null);
    ParseConfig config = ParseTaskUtils.wait(configTask);

    // Verify httpClient is called
    verify(restClient, times(1)).execute(any(ParseHttpRequest.class));
    // Verify currentConfigController is called
    verify(currentConfigController, times(1)).setCurrentConfigAsync(eq(config));
    // Verify ParseConfig we get, do not use ParseConfig getter to keep test separately
    Map<String, Object> paramsAgain = config.getParams();
    assertEquals(10, paramsAgain.size());
    assertEquals("value", paramsAgain.get("string"));
    assertEquals(42, paramsAgain.get("int"));
    assertEquals(0.2778, paramsAgain.get("double"));
    assertTrue((Boolean) paramsAgain.get("trueBool"));
    assertFalse((Boolean) paramsAgain.get("falseBool"));
    assertEquals(date, paramsAgain.get("date"));
    ParseFile fileAgain = (ParseFile) paramsAgain.get("file");
    assertEquals(file.getUrl(), fileAgain.getUrl());
    assertEquals(file.getName(), fileAgain.getName());
    ParseGeoPoint geoPointAgain = (ParseGeoPoint) paramsAgain.get("geoPoint");
    assertEquals(geoPoint.getLatitude(), geoPointAgain.getLatitude(), 0.0000001);
    assertEquals(geoPoint.getLongitude(), geoPointAgain.getLongitude(), 0.0000001);
    List<Object> listAgain = (List<Object>) paramsAgain.get("array");
    assertArrayEquals(list.toArray(), listAgain.toArray());
    Map<String, Object> mapAgain = (Map<String, Object>) paramsAgain.get("object");
    assertEquals(map.size(), mapAgain.size());
    for (Map.Entry<String, Object> entry : map.entrySet()) {
      assertEquals(entry.getValue(), mapAgain.get(entry.getKey()));
    }
  }

  @Test
  public void testGetAsyncFailureWithConnectionFailure() throws Exception {
    // TODO(mengyan): Remove once we no longer rely on retry logic.
    ParseRequest.setDefaultInitialRetryDelay(1L);

    // Make ParseConfigController and call getAsync
    ParseHttpClient restClient = mock(ParseHttpClient.class);
    when(restClient.execute(any(ParseHttpRequest.class))).thenThrow(new IOException());
    ParseCurrentConfigController currentConfigController = mockParseCurrentConfigController();
    ParseConfigController configController =
        new ParseConfigController(restClient, currentConfigController);
    Task<ParseConfig> configTask = configController.getAsync(null);
    // Do not use ParseTaskUtils.wait() since we do not want to throw the exception
    configTask.waitForCompletion();

    // Verify httpClient is tried enough times
    // TODO(mengyan): Abstract out command runner so we don't have to account for retries.
    verify(restClient, times(5)).execute(any(ParseHttpRequest.class));
    assertTrue(configTask.isFaulted());
    Exception error = configTask.getError();
    assertThat(error, instanceOf(ParseException.class));
    assertEquals(ParseException.CONNECTION_FAILED, ((ParseException) error).getCode());
    // Verify currentConfigController is not called
    verify(currentConfigController, times(0)).setCurrentConfigAsync(any(ParseConfig.class));
  }

  //endregion

  private ParseCurrentConfigController mockParseCurrentConfigController() {
    ParseCurrentConfigController currentConfigController = mock(ParseCurrentConfigController.class);
    when(currentConfigController.setCurrentConfigAsync(any(ParseConfig.class)))
        .thenReturn(Task.<Void>forResult(null));
    return currentConfigController;
  }

  //TODO(mengyan) Create unit test helper and move all similar methods to the class
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
}

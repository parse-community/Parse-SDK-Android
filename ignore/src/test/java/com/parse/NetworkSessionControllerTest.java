/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

// For Uri.encode
@RunWith(RobolectricTestRunner.class)
@Config(constants = BuildConfig.class, sdk = TestHelper.ROBOLECTRIC_SDK_VERSION)
public class NetworkSessionControllerTest {

  @Before
  public void setUp() throws MalformedURLException {
    ParseRESTCommand.server = new URL("https://api.parse.com/1");
  }

  @After
  public void tearDown() {
    ParseRESTCommand.server = null;
  }

  //region testGetSessionAsync

  @Test
  public void testGetSessionAsync() throws Exception {
    // Make mock response and client
    JSONObject mockResponse = generateBasicMockResponse();
    mockResponse.put("installationId", "39c8e8a4-6dd0-4c39-ac85-7fd61425083b");
    ParseHttpClient restClient =
        ParseTestUtils.mockParseHttpClientWithResponse(mockResponse, 200, "OK");

    NetworkSessionController controller = new NetworkSessionController(restClient);
    ParseObject.State newState =
        ParseTaskUtils.wait(controller.getSessionAsync("sessionToken"));

    // Verify session state
    verifyBasicSessionState(mockResponse, newState);
    assertEquals("39c8e8a4-6dd0-4c39-ac85-7fd61425083b", newState.get("installationId"));

    assertTrue(newState.isComplete());

  }

  //endregion

  //region testUpgradeToRevocable

  @Test
  public void testUpgradeToRevocable() throws Exception {
    // Make mock response and client
    JSONObject mockResponse = generateBasicMockResponse();
    ParseHttpClient restClient =
        ParseTestUtils.mockParseHttpClientWithResponse(mockResponse, 200, "OK");

    NetworkSessionController controller = new NetworkSessionController(restClient);
    ParseObject.State newState =
        ParseTaskUtils.wait(controller.upgradeToRevocable("sessionToken"));

    // Verify session state
    verifyBasicSessionState(mockResponse, newState);

    assertTrue(newState.isComplete());

  }

  //endregion

  //region testRevokeAsync

  @Test
  public void testRevokeAsync() throws Exception {
    // Make mock response and client
    JSONObject mockResponse = new JSONObject();
    ParseHttpClient restClient =
        ParseTestUtils.mockParseHttpClientWithResponse(mockResponse, 200, "OK");

    NetworkSessionController controller = new NetworkSessionController(restClient);
    // We just need to verify task is finished since sever returns an empty json here
    ParseTaskUtils.wait(controller.revokeAsync("sessionToken"));
  }

  //endregion

  private static JSONObject generateBasicMockResponse() throws JSONException {
    JSONObject mockResponse = new JSONObject();
    mockResponse.put("createdAt", "2015-08-09T22:15:13.460Z");
    mockResponse.put("objectId", "testObjectId");
    mockResponse.put("sessionToken", "r:aBnrECraOBEXJSNMdtQJW36Re");
    mockResponse.put("restricted", "false");
    JSONObject createWith = new JSONObject();
    createWith.put("action", "upgrade");
    mockResponse.put("createdWith", createWith);
    return mockResponse;
  }

  private static void verifyBasicSessionState(JSONObject mockResponse, ParseSession.State state)
      throws JSONException {
    assertEquals("_Session", state.className());
    long createAtLong =
        ParseDateFormat.getInstance().parse(mockResponse.getString("createdAt")).getTime();
    assertEquals(createAtLong, state.createdAt());
    assertEquals(mockResponse.getString("objectId"), state.objectId());
    assertEquals(mockResponse.getString("sessionToken"), state.get("sessionToken"));
    assertEquals(mockResponse.getString("restricted"), state.get("restricted"));
    assertEquals(
        mockResponse.getJSONObject("createdWith").getString("action"),
        ((Map<String, String>)state.get("createdWith")).get("action"));
  }
}

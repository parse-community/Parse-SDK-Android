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
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

// For Uri.encode
@RunWith(RobolectricTestRunner.class)
@Config(constants = BuildConfig.class, sdk = TestHelper.ROBOLECTRIC_SDK_VERSION)
public class NetworkUserControllerTest {

    @Before
    public void setUp() throws MalformedURLException {
        ParseRESTCommand.server = new URL("https://api.parse.com/1");
    }

    @After
    public void tearDown() {
        ParseRESTCommand.server = null;
    }

    //region testSignUpAsync

    @Test
    public void testSignUpAsync() throws Exception {
        // Make mock response and client
        JSONObject mockResponse = generateBasicMockResponse();
        ParseHttpClient restClient =
                ParseTestUtils.mockParseHttpClientWithResponse(generateBasicMockResponse(), 200, "OK");
        // Make test user state and operationSet
        ParseUser.State state = new ParseUser.State.Builder()
                .put("username", "testUserName")
                .put("password", "testPassword")
                .build();
        ParseOperationSet operationSet = new ParseOperationSet();
        operationSet.put("username", new ParseSetOperation("testUserName"));
        operationSet.put("password", new ParseSetOperation("testPassword"));

        NetworkUserController controller = new NetworkUserController(restClient, true);
        ParseUser.State newState = ParseTaskUtils.wait(
                controller.signUpAsync(state, operationSet, "sessionToken"));

        verifyBasicUserState(mockResponse, newState);
        assertFalse(newState.isComplete());
        assertTrue(newState.isNew());
    }

    //endregion

    //region testLoginAsync

    @Test
    public void testLoginAsyncWithUserNameAndPassword() throws Exception {
        // Make mock response and client
        JSONObject mockResponse = generateBasicMockResponse();
        ParseHttpClient restClient =
                ParseTestUtils.mockParseHttpClientWithResponse(mockResponse, 200, "OK");

        NetworkUserController controller = new NetworkUserController(restClient);
        ParseUser.State newState = ParseTaskUtils.wait(controller.logInAsync("userName", "password"));

        // Verify user state
        verifyBasicUserState(mockResponse, newState);

        assertTrue(newState.isComplete());
        assertFalse(newState.isNew());
    }

    @Test
    public void testLoginAsyncWithUserStateCreated() throws Exception {
        // Make mock response and client
        JSONObject mockResponse = generateBasicMockResponse();
        ParseHttpClient restClient =
                ParseTestUtils.mockParseHttpClientWithResponse(mockResponse, 201, "OK");
        // Make test user state and operationSet
        ParseUser.State state = new ParseUser.State.Builder()
                .put("username", "testUserName")
                .put("password", "testPassword")
                .build();
        ParseOperationSet operationSet = new ParseOperationSet();
        operationSet.put("username", new ParseSetOperation("testUserName"));
        operationSet.put("password", new ParseSetOperation("testPassword"));

        NetworkUserController controller = new NetworkUserController(restClient, true);
        ParseUser.State newState = ParseTaskUtils.wait(controller.logInAsync(state, operationSet));

        // Verify user state
        verifyBasicUserState(mockResponse, newState);

        assertTrue(newState.isNew());
        assertFalse(newState.isComplete());
    }

    @Test
    public void testLoginAsyncWithUserStateNotCreated() throws Exception {
        // Make mock response and client
        JSONObject mockResponse = generateBasicMockResponse();
        ParseHttpClient restClient =
                ParseTestUtils.mockParseHttpClientWithResponse(mockResponse, 200, "OK");
        // Make test user state and operationSet
        ParseUser.State state = new ParseUser.State.Builder()
                .put("username", "testUserName")
                .put("password", "testPassword")
                .build();
        ParseOperationSet operationSet = new ParseOperationSet();
        operationSet.put("username", new ParseSetOperation("testUserName"));
        operationSet.put("password", new ParseSetOperation("testPassword"));

        NetworkUserController controller = new NetworkUserController(restClient, true);
        ParseUser.State newState = ParseTaskUtils.wait(controller.logInAsync(state, operationSet));

        // Verify user state
        verifyBasicUserState(mockResponse, newState);

        assertFalse(newState.isNew());
        assertTrue(newState.isComplete());
    }

    @Test
    public void testLoginAsyncWithAuthTypeCreated() throws Exception {
        // Make mock response and client
        JSONObject mockResponse = generateBasicMockResponse();
        mockResponse.put("authData", generateMockAuthData());
        ParseHttpClient restClient =
                ParseTestUtils.mockParseHttpClientWithResponse(mockResponse, 201, "OK");
        // Make test user auth data
        Map<String, String> facebookAuthInfoMap = new HashMap<>();
        facebookAuthInfoMap.put("token", "test");

        NetworkUserController controller = new NetworkUserController(restClient, true);
        ParseUser.State newState = ParseTaskUtils.wait(
                controller.logInAsync("facebook", facebookAuthInfoMap));

        // Verify user state
        verifyBasicUserState(mockResponse, newState);

        assertTrue(newState.isNew());
        assertTrue(newState.isComplete());
    }

    @Test
    public void testLoginAsyncWithAuthTypeNotCreated() throws Exception {
        // Make mock response and client
        JSONObject mockResponse = generateBasicMockResponse();
        mockResponse.put("authData", generateMockAuthData());
        ParseHttpClient restClient =
                ParseTestUtils.mockParseHttpClientWithResponse(mockResponse, 200, "OK");
        // Make test user auth data
        Map<String, String> facebookAuthInfoMap = new HashMap<>();
        facebookAuthInfoMap.put("token", "test");

        NetworkUserController controller = new NetworkUserController(restClient, true);
        ParseUser.State newState = ParseTaskUtils.wait(
                controller.logInAsync("facebook", facebookAuthInfoMap));

        // Verify user state
        verifyBasicUserState(mockResponse, newState);

        assertFalse(newState.isNew());
        assertTrue(newState.isComplete());
    }

    //endregion

    //region testGetUserAsync

    @Test
    public void testGetUserAsync() throws Exception {
        // Make mock response and client
        JSONObject mockResponse = generateBasicMockResponse();
        ParseHttpClient restClient =
                ParseTestUtils.mockParseHttpClientWithResponse(mockResponse, 201, "OK");

        NetworkUserController controller = new NetworkUserController(restClient, true);
        ParseUser.State newState = ParseTaskUtils.wait(controller.getUserAsync("sessionToken"));

        // Verify user state
        verifyBasicUserState(mockResponse, newState);

        assertTrue(newState.isComplete());
        assertFalse(newState.isNew());
    }

    //endregion

    //region testRequestPasswordResetAsync

    @Test
    public void testRequestPasswordResetAsync() throws Exception {
        // Make mock response and client
        ParseHttpClient restClient =
                ParseTestUtils.mockParseHttpClientWithResponse(new JSONObject(), 200, "OK");

        NetworkUserController controller = new NetworkUserController(restClient, true);
        // We just need to verify task is finished since sever returns an empty json here
        ParseTaskUtils.wait(controller.requestPasswordResetAsync("sessionToken"));
    }

    //endregion

    private JSONObject generateBasicMockResponse() throws JSONException {
        JSONObject mockResponse = new JSONObject();
        String createAtStr = "2015-08-09T22:15:13.460Z";
        mockResponse.put("createdAt", createAtStr);
        mockResponse.put("objectId", "testObjectId");
        mockResponse.put("sessionToken", "testSessionToken");
        mockResponse.put("username", "testUserName");
        mockResponse.put("email", "test@parse.com");
        return mockResponse;
    }

    private JSONObject generateMockAuthData() throws JSONException {
        JSONObject facebookAuthInfo = new JSONObject();
        facebookAuthInfo.put("token", "test");
        JSONObject facebookAuthData = new JSONObject();
        facebookAuthData.put("facebook", facebookAuthInfo);
        return facebookAuthData;
    }

    private void verifyBasicUserState(JSONObject mockResponse, ParseUser.State state)
            throws JSONException {
        long createAtLong =
                ParseDateFormat.getInstance().parse(mockResponse.getString("createdAt")).getTime();
        assertEquals(createAtLong, state.createdAt());
        assertEquals(mockResponse.getString("objectId"), state.objectId());
        assertEquals(mockResponse.getString("sessionToken"), state.sessionToken());
        assertEquals(mockResponse.getString("username"), state.get("username"));
        assertEquals(mockResponse.getString("email"), state.get("email"));
    }
}

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
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.skyscreamer.jsonassert.JSONCompareMode;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import com.parse.boltsinternal.Task;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.skyscreamer.jsonassert.JSONAssert.assertEquals;

// For org.json
@RunWith(RobolectricTestRunner.class)
@Config(constants = BuildConfig.class, sdk = TestHelper.ROBOLECTRIC_SDK_VERSION)
@SuppressWarnings("ThrowableResultOfMethodCallIgnored")
public class ParseRESTCommandTest {

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private static ParseHttpResponse newMockParseHttpResponse(int statusCode, JSONObject body) {
        return newMockParseHttpResponse(statusCode, body.toString());
    }

    private static ParseHttpResponse newMockParseHttpResponse(int statusCode, String body) {
        return new ParseHttpResponse.Builder()
                .setStatusCode(statusCode)
                .setTotalSize((long) body.length())
                .setContent(new ByteArrayInputStream(body.getBytes()))
                .build();
    }

    @Before
    public void setUp() throws Exception {
        ParseRequest.setDefaultInitialRetryDelay(1L);
        ParseRESTCommand.server = new URL("https://api.parse.com/1");
    }

    @After
    public void tearDown() {
        ParseRequest.setDefaultInitialRetryDelay(ParseRequest.DEFAULT_INITIAL_RETRY_DELAY);
        ParseCorePlugins.getInstance().reset();
        ParseRESTCommand.server = null;
    }


    @Test
    public void testInitializationWithDefaultParseServerURL() throws Exception {
        ParseRESTCommand.server = new URL("https://api.parse.com/1/");
        ParseRESTCommand command = new ParseRESTCommand.Builder()
                .httpPath("events/Appopened")
                .build();

        assertEquals("https://api.parse.com/1/events/Appopened", command.url);
    }

    @Test
    public void testPermanentFailures() throws Exception {
        JSONObject json = new JSONObject();
        json.put("code", 1337);
        json.put("error", "mock error");

        ParseHttpResponse response = newMockParseHttpResponse(400, json);
        ParseHttpClient client = mock(ParseHttpClient.class);
        when(client.execute(any(ParseHttpRequest.class))).thenReturn(response);

        ParseRESTCommand command = new ParseRESTCommand.Builder()
                .method(ParseHttpRequest.Method.GET)
                .installationId("fake_installation_id")
                .build();
        Task<JSONObject> task = command.executeAsync(client);
        task.waitForCompletion();
        verify(client, times(1)).execute(any(ParseHttpRequest.class));

        assertTrue(task.isFaulted());
        assertEquals(1337, ((ParseException) task.getError()).getCode());
        assertEquals("mock error", task.getError().getMessage());
    }

    @Test
    public void testTemporaryFailures() throws Exception {
        JSONObject json = new JSONObject();
        json.put("code", 1337);
        json.put("error", "mock error");

        ParseHttpResponse response1 = newMockParseHttpResponse(500, json);
        ParseHttpResponse response2 = newMockParseHttpResponse(500, json);
        ParseHttpResponse response3 = newMockParseHttpResponse(500, json);
        ParseHttpResponse response4 = newMockParseHttpResponse(500, json);
        ParseHttpResponse response5 = newMockParseHttpResponse(500, json);
        ParseHttpClient client = mock(ParseHttpClient.class);
        when(client.execute(any(ParseHttpRequest.class))).thenReturn(
                response1,
                response2,
                response3,
                response4,
                response5
        );

        ParseRESTCommand command = new ParseRESTCommand.Builder()
                .method(ParseHttpRequest.Method.GET)
                .installationId("fake_installation_id")
                .build();
        Task<JSONObject> task = command.executeAsync(client);
        task.waitForCompletion();
        verify(client, times(5)).execute(any(ParseHttpRequest.class));

        assertTrue(task.isFaulted());
        assertEquals(1337, ((ParseException) task.getError()).getCode());
        assertEquals("mock error", task.getError().getMessage());
    }

    /**
     * Test to verify that handle 401 unauthorized
     */
    @Test
    public void test401Unauthorized() throws Exception {
        JSONObject json = new JSONObject();
        json.put("error", "unauthorized");

        ParseHttpResponse response = newMockParseHttpResponse(401, json);
        ParseHttpClient client = mock(ParseHttpClient.class);
        when(client.execute(any(ParseHttpRequest.class))).thenReturn(response);

        ParseRESTCommand command = new ParseRESTCommand.Builder()
                .method(ParseHttpRequest.Method.GET)
                .installationId("fake_installation_id")
                .build();
        Task<JSONObject> task = command.executeAsync(client);
        task.waitForCompletion();
        verify(client, times(1)).execute(any(ParseHttpRequest.class));

        assertTrue(task.isFaulted());
        assertEquals(0, ((ParseException) task.getError()).getCode());
        assertEquals("unauthorized", task.getError().getMessage());
    }

    @Test
    public void testToDeterministicString() throws Exception {
        // Make test json
        JSONArray nestedJSONArray = new JSONArray()
                .put(true)
                .put(1)
                .put("test");
        JSONObject nestedJSON = new JSONObject()
                .put("bool", false)
                .put("int", 2)
                .put("string", "test");
        JSONObject json = new JSONObject()
                .put("json", nestedJSON)
                .put("jsonArray", nestedJSONArray)
                .put("bool", true)
                .put("int", 3)
                .put("string", "test");

        String jsonString = ParseRESTCommand.toDeterministicString(json);

        JSONObject jsonAgain = new JSONObject(jsonString);
        assertEquals(json, jsonAgain, JSONCompareMode.NON_EXTENSIBLE);
    }

    @Test
    public void testToJSONObject() throws Exception {
        // Make test command
        String httpPath = "www.parse.com";
        JSONObject jsonParameters = new JSONObject()
                .put("count", 1)
                .put("limit", 1);
        String sessionToken = "sessionToken";
        String localId = "localId";
        ParseRESTCommand command = new ParseRESTCommand.Builder()
                .httpPath(httpPath)
                .jsonParameters(jsonParameters)
                .method(ParseHttpRequest.Method.POST)
                .sessionToken(sessionToken)
                .localId(localId)
                .build();

        JSONObject json = command.toJSONObject();

        assertEquals(httpPath, json.getString("httpPath"));
        assertEquals("POST", json.getString("httpMethod"));
        assertEquals(jsonParameters, json.getJSONObject("parameters"), JSONCompareMode.NON_EXTENSIBLE);
        assertEquals(sessionToken, json.getString("sessionToken"));
        assertEquals(localId, json.getString("localId"));
    }

    @Test
    public void testGetCacheKey() throws Exception {
        // Make test command
        String httpPath = "www.parse.com";
        JSONObject jsonParameters = new JSONObject()
                .put("count", 1)
                .put("limit", 1);
        String sessionToken = "sessionToken";
        String localId = "localId";
        ParseRESTCommand command = new ParseRESTCommand.Builder()
                .httpPath(httpPath)
                .jsonParameters(jsonParameters)
                .method(ParseHttpRequest.Method.POST)
                .sessionToken(sessionToken)
                .localId(localId)
                .build();

        String cacheKey = command.getCacheKey();

        assertTrue(cacheKey.contains("ParseRESTCommand"));
        assertTrue(cacheKey.contains(ParseHttpRequest.Method.POST.toString()));
        assertTrue(cacheKey.contains(ParseDigestUtils.md5(httpPath)));
        String str =
                ParseDigestUtils.md5(ParseRESTCommand.toDeterministicString(jsonParameters) + sessionToken);
        assertTrue(cacheKey.contains(str));
    }

    @Test
    public void testGetCacheKeyWithNoJSONParameters() {
        // Make test command
        String httpPath = "www.parse.com";
        String sessionToken = "sessionToken";
        String localId = "localId";
        ParseRESTCommand command = new ParseRESTCommand.Builder()
                .httpPath(httpPath)
                .method(ParseHttpRequest.Method.POST)
                .sessionToken(sessionToken)
                .localId(localId)
                .build();

        String cacheKey = command.getCacheKey();

        assertTrue(cacheKey.contains("ParseRESTCommand"));
        assertTrue(cacheKey.contains(ParseHttpRequest.Method.POST.toString()));
        assertTrue(cacheKey.contains(ParseDigestUtils.md5(httpPath)));
        assertTrue(cacheKey.contains(ParseDigestUtils.md5(sessionToken)));
    }

    @Test
    public void testReleaseLocalIds() {
        // Register LocalIdManager
        LocalIdManager localIdManager = mock(LocalIdManager.class);
        when(localIdManager.createLocalId()).thenReturn("localIdAgain");
        ParseCorePlugins.getInstance().registerLocalIdManager(localIdManager);

        // Make test command
        ParseObject object = new ParseObject("Test");
        object.put("key", "value");
        String httpPath = "www.parse.com";
        JSONObject jsonParameters = PointerOrLocalIdEncoder.get().encodeRelatedObject(object);
        String sessionToken = "sessionToken";
        String localId = "localId";

        ParseRESTCommand command = new ParseRESTCommand.Builder()
                .httpPath(httpPath)
                .jsonParameters(jsonParameters)
                .method(ParseHttpRequest.Method.POST)
                .sessionToken(sessionToken)
                .localId(localId)
                .build();

        command.releaseLocalIds();

        verify(localIdManager, times(1)).releaseLocalIdOnDisk(localId);
        verify(localIdManager, times(1)).releaseLocalIdOnDisk("localIdAgain");
    }

    @Test
    public void testResolveLocalIdsWithNoObjectId() {
        // Register LocalIdManager
        LocalIdManager localIdManager = mock(LocalIdManager.class);
        when(localIdManager.createLocalId()).thenReturn("localIdAgain");
        ParseCorePlugins.getInstance().registerLocalIdManager(localIdManager);

        // Make test command
        ParseObject object = new ParseObject("Test");
        object.put("key", "value");
        String httpPath = "www.parse.com";
        JSONObject jsonParameters = PointerOrLocalIdEncoder.get().encodeRelatedObject(object);
        String sessionToken = "sessionToken";
        String localId = "localId";

        ParseRESTCommand command = new ParseRESTCommand.Builder()
                .httpPath(httpPath)
                .jsonParameters(jsonParameters)
                .method(ParseHttpRequest.Method.POST)
                .sessionToken(sessionToken)
                .localId(localId)
                .build();

        thrown.expect(IllegalStateException.class);
        thrown.expectMessage("Tried to serialize a command referencing a new, unsaved object.");
        command.resolveLocalIds();

        // Make sure we try to get the objectId
        verify(localIdManager, times(1)).getObjectId("localIdAgain");
    }

    @Test
    public void testResolveLocalIds() throws Exception {
        // Register LocalIdManager
        LocalIdManager localIdManager = mock(LocalIdManager.class);
        when(localIdManager.createLocalId()).thenReturn("localIdAgain");
        when(localIdManager.getObjectId("localIdAgain")).thenReturn("objectIdAgain");
        when(localIdManager.getObjectId("localId")).thenReturn("objectId");
        ParseCorePlugins.getInstance().registerLocalIdManager(localIdManager);

        // Make test command
        ParseObject object = new ParseObject("Test");
        object.put("key", "value");
        String httpPath = "classes";
        JSONObject jsonParameters = PointerOrLocalIdEncoder.get().encodeRelatedObject(object);
        String sessionToken = "sessionToken";
        String localId = "localId";

        ParseRESTCommand command = new ParseRESTCommand.Builder()
                .httpPath(httpPath)
                .jsonParameters(jsonParameters)
                .method(ParseHttpRequest.Method.POST)
                .sessionToken(sessionToken)
                .localId(localId)
                .build();

        command.resolveLocalIds();

        verify(localIdManager, times(1)).getObjectId("localIdAgain");
        verify(localIdManager, times(1)).getObjectId("localId");
        // Make sure localId in jsonParameters has been replaced with objectId
        assertFalse(jsonParameters.has("localId"));
        assertEquals("objectIdAgain", jsonParameters.getString("objectId"));
        // Make sure localId in command has been replaced with objectId
        assertNull(command.getLocalId());
        // Make sure httpMethod has been changed
        assertEquals(ParseHttpRequest.Method.PUT, command.method);
        // Make sure objectId has been added to httpPath
        assertTrue(command.httpPath.contains("objectId"));
    }

    @Test
    public void testRetainLocalIds() {
        // Register LocalIdManager
        LocalIdManager localIdManager = mock(LocalIdManager.class);
        when(localIdManager.createLocalId()).thenReturn("localIdAgain");
        ParseCorePlugins.getInstance().registerLocalIdManager(localIdManager);

        // Make test command
        ParseObject object = new ParseObject("Test");
        object.put("key", "value");
        String httpPath = "classes";
        JSONObject jsonParameters = PointerOrLocalIdEncoder.get().encodeRelatedObject(object);
        String sessionToken = "sessionToken";
        String localId = "localId";

        ParseRESTCommand command = new ParseRESTCommand.Builder()
                .httpPath(httpPath)
                .jsonParameters(jsonParameters)
                .method(ParseHttpRequest.Method.POST)
                .sessionToken(sessionToken)
                .localId(localId)
                .build();

        command.retainLocalIds();

        verify(localIdManager, times(1)).retainLocalIdOnDisk("localIdAgain");
        verify(localIdManager, times(1)).retainLocalIdOnDisk(localId);
    }

    @Test
    public void testNewBodyWithNoJSONParameters() {
        // Make test command
        String httpPath = "www.parse.com";
        String sessionToken = "sessionToken";
        String localId = "localId";
        ParseRESTCommand command = new ParseRESTCommand.Builder()
                .httpPath(httpPath)
                .method(ParseHttpRequest.Method.GET)
                .sessionToken(sessionToken)
                .localId(localId)
                .build();

        thrown.expect(IllegalArgumentException.class);
        String message = String.format("Trying to execute a %s command without body parameters.",
                ParseHttpRequest.Method.GET.toString());
        thrown.expectMessage(message);

        command.newBody(null);
    }

    @Test
    public void testNewBody() throws Exception {
        // Make test command
        String httpPath = "www.parse.com";
        JSONObject jsonParameters = new JSONObject()
                .put("count", 1)
                .put("limit", 1);
        String sessionToken = "sessionToken";
        String localId = "localId";
        ParseRESTCommand command = new ParseRESTCommand.Builder()
                .httpPath(httpPath)
                .jsonParameters(jsonParameters)
                .method(ParseHttpRequest.Method.GET)
                .sessionToken(sessionToken)
                .localId(localId)
                .build();

        ParseByteArrayHttpBody body = (ParseByteArrayHttpBody) command.newBody(null);

        // Verify body content is correct
        JSONObject json = new JSONObject(new String(ParseIOUtils.toByteArray(body.getContent())));
        assertEquals(1, json.getInt("count"));
        assertEquals(1, json.getInt("limit"));
        assertEquals(ParseHttpRequest.Method.GET.toString(), json.getString("_method"));
        // Verify body content-type is correct
        assertEquals("application/json", body.getContentType());
    }

    @Test
    public void testFromJSONObject() throws Exception {
        // Make test command
        String httpPath = "www.parse.com";
        JSONObject jsonParameters = new JSONObject()
                .put("count", 1)
                .put("limit", 1);
        String sessionToken = "sessionToken";
        String localId = "localId";
        String httpMethod = "POST";
        JSONObject commandJSON = new JSONObject()
                .put("httpPath", httpPath)
                .put("parameters", jsonParameters)
                .put("httpMethod", httpMethod)
                .put("sessionToken", sessionToken)
                .put("localId", localId);

        ParseRESTCommand command = ParseRESTCommand.fromJSONObject(commandJSON);

        assertEquals(httpPath, command.httpPath);
        assertEquals(httpMethod, command.method.toString());
        assertEquals(sessionToken, command.getSessionToken());
        assertEquals(localId, command.getLocalId());
        assertEquals(jsonParameters, command.jsonParameters, JSONCompareMode.NON_EXTENSIBLE);
    }

    @Test
    public void testOnResponseCloseNetworkStreamWithNormalResponse() throws Exception {
        // Mock response stream
        int statusCode = 200;
        JSONObject bodyJson = new JSONObject();
        bodyJson.put("key", "value");
        String bodyStr = bodyJson.toString();
        ByteArrayInputStream bodyStream = new ByteArrayInputStream(bodyStr.getBytes());
        InputStream mockResponseStream = spy(bodyStream);
        doNothing()
                .when(mockResponseStream)
                .close();
        // Mock response
        ParseHttpResponse mockResponse = new ParseHttpResponse.Builder()
                .setStatusCode(statusCode)
                .setTotalSize((long) bodyStr.length())
                .setContent(mockResponseStream)
                .build();

        ParseRESTCommand command = new ParseRESTCommand.Builder().build();
        JSONObject json = ParseTaskUtils.wait(command.onResponseAsync(mockResponse, null));

        verify(mockResponseStream, times(1)).close();
        assertEquals(bodyJson, json, JSONCompareMode.NON_EXTENSIBLE);
    }

    @Test
    public void testOnResposneCloseNetworkStreamWithIOException() throws Exception {
        // Mock response stream
        int statusCode = 200;
        InputStream mockResponseStream = mock(InputStream.class);
        doNothing()
                .when(mockResponseStream)
                .close();
        IOException readException = new IOException("Error");
        doThrow(readException)
                .when(mockResponseStream)
                .read();
        doThrow(readException)
                .when(mockResponseStream)
                .read(any(byte[].class));
        // Mock response
        ParseHttpResponse mockResponse = new ParseHttpResponse.Builder()
                .setStatusCode(statusCode)
                .setContent(mockResponseStream)
                .build();

        ParseRESTCommand command = new ParseRESTCommand.Builder().build();
        // We can not use ParseTaskUtils here since it will replace the original exception with runtime
        // exception
        Task<JSONObject> responseTask = command.onResponseAsync(mockResponse, null);
        responseTask.waitForCompletion();

        assertTrue(responseTask.isFaulted());
        assertTrue(responseTask.getError() instanceof IOException);
        assertEquals("Error", responseTask.getError().getMessage());
        verify(mockResponseStream, times(1)).close();
    }
}

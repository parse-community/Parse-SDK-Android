/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.annotation.Config;
import org.skyscreamer.jsonassert.JSONCompareMode;

import java.io.ByteArrayInputStream;

import bolts.Task;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.skyscreamer.jsonassert.JSONAssert.assertEquals;

// For org.json
@RunWith(RobolectricGradleTestRunner.class)
@Config(constants = BuildConfig.class, sdk = 21)
@SuppressWarnings("ThrowableResultOfMethodCallIgnored")
public class ParseRESTCommandTest {

  private static ParseHttpResponse newMockParseHttpResponse(int statusCode, JSONObject body) {
    return newMockParseHttpResponse(statusCode, body.toString());
  }

  private static ParseHttpResponse newMockParseHttpResponse(int statusCode, String body) {
    ParseHttpResponse response = mock(ParseHttpResponse.class);
    when(response.getStatusCode()).thenReturn(statusCode);
    when(response.getContent()).thenReturn(new ByteArrayInputStream(body.getBytes()));
    when(response.getTotalSize()).thenReturn(body.length());
    return response;
  }

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Before
  public void setUp() throws Exception {
    ParseRequest.setDefaultInitialRetryDelay(1L);
  }

  @After
  public void tearDown() throws Exception {
    ParseRequest.setDefaultInitialRetryDelay(ParseRequest.DEFAULT_INITIAL_RETRY_DELAY);
    ParseCorePlugins.getInstance().reset();
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
        .method(ParseRequest.Method.GET)
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
        .method(ParseRequest.Method.GET)
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
        .method(ParseRequest.Method.GET)
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
        .method(ParseRequest.Method.POST)
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
        .method(ParseRequest.Method.POST)
        .sessionToken(sessionToken)
        .localId(localId)
        .build();

    String cacheKey = command.getCacheKey();

    assertTrue(cacheKey.contains("ParseRESTCommand"));
    assertTrue(cacheKey.contains(ParseRequest.Method.POST.toString()));
    assertTrue(cacheKey.contains(ParseDigestUtils.md5(httpPath)));
    String str =
        ParseDigestUtils.md5(ParseRESTCommand.toDeterministicString(jsonParameters) + sessionToken);
    assertTrue(cacheKey.contains(str));
  }

  @Test
  public void testGetCacheKeyWithNoJSONParameters() throws Exception {
    // Make test command
    String httpPath = "www.parse.com";
    String sessionToken = "sessionToken";
    String localId = "localId";
    ParseRESTCommand command = new ParseRESTCommand.Builder()
        .httpPath(httpPath)
        .method(ParseRequest.Method.POST)
        .sessionToken(sessionToken)
        .localId(localId)
        .build();

    String cacheKey = command.getCacheKey();

    assertTrue(cacheKey.contains("ParseRESTCommand"));
    assertTrue(cacheKey.contains(ParseRequest.Method.POST.toString()));
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
        .method(ParseRequest.Method.POST)
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
        .method(ParseRequest.Method.POST)
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
        .method(ParseRequest.Method.POST)
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
    assertEquals(ParseRequest.Method.PUT, command.method);
    // Make sure objectId has been added to httpPath
    assertTrue(command.httpPath.contains("objectId"));
  }

  @Test
  public void testRetainLocalIds() throws Exception {
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
        .method(ParseRequest.Method.POST)
        .sessionToken(sessionToken)
        .localId(localId)
        .build();

    command.retainLocalIds();

    verify(localIdManager, times(1)).retainLocalIdOnDisk("localIdAgain");
    verify(localIdManager, times(1)).retainLocalIdOnDisk(localId);
  }

  @Test
  public void testNewBodyWithNoJSONParameters() throws Exception {
    // Make test command
    String httpPath = "www.parse.com";
    String sessionToken = "sessionToken";
    String localId = "localId";
    ParseRESTCommand command = new ParseRESTCommand.Builder()
        .httpPath(httpPath)
        .method(ParseRequest.Method.GET)
        .sessionToken(sessionToken)
        .localId(localId)
        .build();

    thrown.expect(IllegalArgumentException.class);
    String message = String.format("Trying to execute a %s command without body parameters.",
        ParseRequest.Method.GET.toString());
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
        .method(ParseRequest.Method.GET)
        .sessionToken(sessionToken)
        .localId(localId)
        .build();

    ParseByteArrayHttpBody body = (ParseByteArrayHttpBody) command.newBody(null);

    // Verify body content is correct
    JSONObject json = new JSONObject(new String(ParseIOUtils.toByteArray(body.getContent())));
    assertEquals(1, json.getInt("count"));
    assertEquals(1, json.getInt("limit"));
    assertEquals(ParseRequest.Method.GET.toString(), json.getString("_method"));
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
}

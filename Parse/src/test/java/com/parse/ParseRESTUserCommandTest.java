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
import org.junit.Test;
import org.skyscreamer.jsonassert.JSONCompareMode;

import java.io.ByteArrayInputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.skyscreamer.jsonassert.JSONAssert.assertEquals;

public class ParseRESTUserCommandTest {

  @Before
  public void setUp() throws MalformedURLException {
    ParseObject.registerSubclass(ParseUser.class);
    ParseRESTCommand.server = new URL("https://api.parse.com/1");
  }

  @After
  public void tearDown() {
    ParseObject.unregisterSubclass(ParseUser.class);
    ParseRESTCommand.server = null;
  }

  //region testConstruct

  @Test
  public void testGetCurrentUserCommand() throws Exception {
    ParseRESTUserCommand command = ParseRESTUserCommand.getCurrentUserCommand("sessionToken");

    assertEquals("users/me", command.httpPath);
    assertEquals(ParseHttpRequest.Method.GET, command.method);
    assertNull(command.jsonParameters);
    assertEquals("sessionToken", command.getSessionToken());
    // TODO(mengyan): Find a way to verify revocableSession
  }

  @Test
  public void testLogInUserCommand() throws Exception {
    ParseRESTUserCommand command = ParseRESTUserCommand.logInUserCommand(
        "userName", "password", true);

    assertEquals("login", command.httpPath);
    assertEquals(ParseHttpRequest.Method.GET, command.method);
    assertEquals("userName", command.jsonParameters.getString("username"));
    assertEquals("password", command.jsonParameters.getString("password"));
    assertNull(command.getSessionToken());
    // TODO(mengyan): Find a way to verify revocableSession
  }

  @Test
  public void testResetPasswordResetCommand() throws Exception {
    ParseRESTUserCommand command = ParseRESTUserCommand.resetPasswordResetCommand("test@parse.com");

    assertEquals("requestPasswordReset", command.httpPath);
    assertEquals(ParseHttpRequest.Method.POST, command.method);
    assertEquals("test@parse.com", command.jsonParameters.getString("email"));
    assertNull(command.getSessionToken());
    // TODO(mengyan): Find a way to verify revocableSession
  }

  @Test
  public void testSignUpUserCommand() throws Exception {
    JSONObject parameters = new JSONObject();
    parameters.put("key", "value");
    ParseRESTUserCommand command =
        ParseRESTUserCommand.signUpUserCommand(parameters, "sessionToken", true);

    assertEquals("users", command.httpPath);
    assertEquals(ParseHttpRequest.Method.POST, command.method);
    assertEquals("value", command.jsonParameters.getString("key"));
    assertEquals("sessionToken", command.getSessionToken());
    // TODO(mengyan): Find a way to verify revocableSession
  }

  @Test
  public void testServiceLogInUserCommandWithParameters() throws Exception {
    JSONObject parameters = new JSONObject();
    parameters.put("key", "value");
    ParseRESTUserCommand command =
        ParseRESTUserCommand.serviceLogInUserCommand(parameters, "sessionToken", true);

    assertEquals("users", command.httpPath);
    assertEquals(ParseHttpRequest.Method.POST, command.method);
    assertEquals("value", command.jsonParameters.getString("key"));
    assertEquals("sessionToken", command.getSessionToken());
    // TODO(mengyan): Find a way to verify revocableSession
  }

  @Test
  public void testServiceLogInUserCommandWithAuthType() throws Exception {
    Map<String, String> facebookAuthData = new HashMap<>();
    facebookAuthData.put("token", "test");
    ParseRESTUserCommand command =
        ParseRESTUserCommand.serviceLogInUserCommand("facebook", facebookAuthData, true);

    assertEquals("users", command.httpPath);
    assertEquals(ParseHttpRequest.Method.POST, command.method);
    assertNull(command.getSessionToken());
    JSONObject authenticationData = new JSONObject();
    authenticationData.put("facebook", PointerEncoder.get().encode(facebookAuthData));
    JSONObject parameters = new JSONObject();
    parameters.put("authData", authenticationData);
    assertEquals(parameters, command.jsonParameters, JSONCompareMode.NON_EXTENSIBLE);
    // TODO(mengyan): Find a way to verify revocableSession
  }

  //endregion

  //region testAddAdditionalHeaders

  @Test
  public void testAddAdditionalHeaders() throws Exception {
    JSONObject parameters = new JSONObject();
    parameters.put("key", "value");
    ParseRESTUserCommand command =
        ParseRESTUserCommand.signUpUserCommand(parameters, "sessionToken", true);

    ParseHttpRequest.Builder requestBuilder  = new ParseHttpRequest.Builder();
    command.addAdditionalHeaders(requestBuilder);

    assertEquals("1", requestBuilder.build().getHeader("X-Parse-Revocable-Session"));
  }

  //endregion

  //region testOnResponseAsync

  @Test
  public void testOnResponseAsync() throws Exception {
    ParseRESTUserCommand command =
        ParseRESTUserCommand.getCurrentUserCommand("sessionToken");

    String content = "content";
    String contentType = "application/json";
    int statusCode = 200;

    ParseHttpResponse response = new ParseHttpResponse.Builder()
        .setContent(new ByteArrayInputStream(content.getBytes()))
        .setContentType(contentType)
        .setStatusCode(statusCode)
        .build();
    command.onResponseAsync(response, null);

    assertEquals(200, command.getStatusCode());
  }

  //endregion
}

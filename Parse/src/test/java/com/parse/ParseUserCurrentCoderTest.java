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

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

public class ParseUserCurrentCoderTest {

  private static final String KEY_AUTH_DATA = "auth_data";
  private static final String KEY_SESSION_TOKEN = "session_token";

  @Test
  public void testEncodeSuccess() throws Exception {
    Map<String, String> facebookAuthData = new HashMap<>();
    facebookAuthData.put("id", "facebookId");
    facebookAuthData.put("access_token", "facebookAccessToken");
    Map<String, String> twitterAuthData = new HashMap<>();
    twitterAuthData.put("id", "twitterId");
    twitterAuthData.put("access_token", "twitterAccessToken");
    ParseUser.State state = new ParseUser.State.Builder()
        .sessionToken("sessionToken")
        .putAuthData("facebook", facebookAuthData)
        .putAuthData("twitter", twitterAuthData)
        .build();

    ParseUserCurrentCoder coder = ParseUserCurrentCoder.get();
    JSONObject objectJson = coder.encode(state, null, PointerEncoder.get());

    assertEquals("sessionToken", objectJson.getString(KEY_SESSION_TOKEN));
    JSONObject authDataJson = objectJson.getJSONObject(KEY_AUTH_DATA);
    JSONObject facebookAuthDataJson = authDataJson.getJSONObject("facebook");
    assertEquals("facebookId", facebookAuthDataJson.getString("id"));
    assertEquals("facebookAccessToken", facebookAuthDataJson.getString("access_token"));
    JSONObject twitterAuthDataJson = authDataJson.getJSONObject("twitter");
    assertEquals("twitterId", twitterAuthDataJson.getString("id"));
    assertEquals("twitterAccessToken", twitterAuthDataJson.getString("access_token"));
  }

  @Test
  public void testEncodeSuccessWithEmptyState() throws Exception {
    ParseUser.State state = new ParseUser.State.Builder()
        .build();

    ParseUserCurrentCoder coder = ParseUserCurrentCoder.get();
    JSONObject objectJson = coder.encode(state, null, PointerEncoder.get());

    assertFalse(objectJson.has(KEY_SESSION_TOKEN));
    assertFalse(objectJson.has(KEY_AUTH_DATA));
  }

  @Test
  public void testDecodeSuccessWithSessionTokenAndAuthData() throws Exception {
    JSONObject facebookAuthDataJson = new JSONObject()
        .put("id", "facebookId")
        .put("access_token", "facebookAccessToken");
    JSONObject twitterAuthDataJson = new JSONObject()
        .put("id", "twitterId")
        .put("access_token", "twitterAccessToken");
    JSONObject authDataJson = new JSONObject()
        .put("facebook", facebookAuthDataJson)
        .put("twitter", twitterAuthDataJson);
    JSONObject objectJson = new JSONObject()
        .put(KEY_SESSION_TOKEN, "sessionToken")
        .put(KEY_AUTH_DATA, authDataJson);

    ParseUserCurrentCoder coder = ParseUserCurrentCoder.get();
    ParseUser.State.Builder builder =
        coder.decode(new ParseUser.State.Builder(), objectJson, ParseDecoder.get());

    // We use the builder to build a state to verify the content in the builder
    ParseUser.State state = builder.build();
    assertEquals("sessionToken", state.sessionToken());
    Map<String, Map<String, String>> authData = state.authData();
    Map<String, String> facebookAuthData = authData.get("facebook");
    assertEquals("facebookId", facebookAuthData.get("id"));
    assertEquals("facebookAccessToken", facebookAuthData.get("access_token"));
    Map<String, String> twitterAuthData = authData.get("twitter");
    assertEquals("twitterId", twitterAuthData.get("id"));
    assertEquals("twitterAccessToken", twitterAuthData.get("access_token"));
  }

  @Test
  public void testDecodeSuccessWithoutSessionTokenAndAuthData() throws Exception {
    JSONObject objectJson = new JSONObject();

    ParseUserCurrentCoder coder = ParseUserCurrentCoder.get();
    ParseUser.State.Builder builder =
        coder.decode(new ParseUser.State.Builder(), objectJson, ParseDecoder.get());

    // We use the builder to build a state to verify the content in the builder
    ParseUser.State state = builder.build();
    assertNull(state.sessionToken());
    // We always return non-null for authData()
    assertEquals(0, state.authData().size());
  }

  @Test
  public void testEncodeDecodeWithNullValues() throws Exception {
    ParseUser.State state = new ParseUser.State.Builder()
        .sessionToken(null)
        .authData(null)
        .build();
    ParseUserCurrentCoder coder = ParseUserCurrentCoder.get();
    JSONObject object = coder.encode(state, null, PointerEncoder.get());
    ParseUser.State.Builder builder =
            coder.decode(new ParseUser.State.Builder(), object, ParseDecoder.get());
    state = builder.build();
    assertNull(state.sessionToken());
    assertEquals(0, state.authData().size());
  }
}

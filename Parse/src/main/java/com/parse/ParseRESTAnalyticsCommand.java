/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import android.net.Uri;

import com.parse.http.ParseHttpRequest;

import org.json.JSONObject;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/** package */ class ParseRESTAnalyticsCommand extends ParseRESTCommand {

  /**
   * The set of predefined events
   */
  // Tracks the AppOpened event
  /* package for test */ static final String EVENT_APP_OPENED = "AppOpened";

  public ParseRESTAnalyticsCommand(
      String httpPath,
      ParseHttpRequest.Method httpMethod,
      Map<String, ?> parameters,
      String sessionToken) {
    super(httpPath, httpMethod, parameters, sessionToken);
  }

  public static ParseRESTAnalyticsCommand trackAppOpenedCommand(String pushHash,
      String sessionToken) {
    Map<String, String> parameters = null;
    if (pushHash != null) {
      parameters = new HashMap<>();
      parameters.put("push_hash", pushHash);
    }
    return trackEventCommand(EVENT_APP_OPENED, parameters, sessionToken);
  }

  public static ParseRESTAnalyticsCommand trackEventCommand(String eventName, JSONObject dimensions,
      String sessionToken) {
    Map<String, Object> parameters = null;
    if (dimensions != null) {
      parameters = new HashMap<>();
      parameters.put("dimensions", dimensions);
    }
    return trackEventCommand(eventName, parameters, sessionToken);
  }

  /* package */ static ParseRESTAnalyticsCommand trackEventCommand(String eventName,
      Map<String, ?> parameters, String sessionToken) {
    String httpPath = String.format("events/%s", Uri.encode(eventName));
    Map<String, Object> commandParameters = new HashMap<>();
    if (parameters != null) {
      commandParameters.putAll(parameters);
    }
    commandParameters.put("at", NoObjectsEncoder.get().encode(new Date()));
    return new ParseRESTAnalyticsCommand(
        httpPath, ParseHttpRequest.Method.POST, commandParameters, sessionToken);
  }
}

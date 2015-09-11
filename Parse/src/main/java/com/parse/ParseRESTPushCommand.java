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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** package */ class ParseRESTPushCommand extends ParseRESTCommand {

  /* package */ final static String KEY_CHANNELS = "channels";
  /* package */ final static String KEY_WHERE = "where";
  /* package */ final static String KEY_DEVICE_TYPE = "deviceType";
  /* package */ final static String KEY_EXPIRATION_TIME = "expiration_time";
  /* package */ final static String KEY_EXPIRATION_INTERVAL = "expiration_interval";
  /* package */ final static String KEY_DATA = "data";

  public ParseRESTPushCommand(
      String httpPath,
      ParseHttpRequest.Method httpMethod,
      Map<String, ?> parameters,
      String sessionToken) {
    super(httpPath, httpMethod, parameters, sessionToken);
  }

  public static ParseRESTPushCommand sendPushCommand(ParseQuery.State<ParseInstallation> query,
  Set<String> targetChannels, String targetDeviceType, Long expirationTime,
      Long expirationInterval, JSONObject payload, String sessionToken) {
    Map <String, Object> parameters = new HashMap<>();

    if (targetChannels != null) {
      parameters.put(KEY_CHANNELS, new JSONArray(targetChannels));
    } else {
      if (query != null) {
        ParseQuery.QueryConstraints where = query.constraints();
        JSONObject whereJSON = (JSONObject) PointerEncoder.get().encode(where);
        parameters.put(KEY_WHERE, whereJSON);
      }
      if (targetDeviceType != null) {
        JSONObject deviceTypeCondition = new JSONObject();
        try {
          deviceTypeCondition.put(KEY_DEVICE_TYPE, targetDeviceType);
        } catch (JSONException e) {
          throw new RuntimeException(e.getMessage());
        }
        parameters.put(KEY_WHERE, deviceTypeCondition);
      }
      if (parameters.size() == 0) {
        // If there are no conditions set, then push to everyone by specifying empty query conditions.
        parameters.put(KEY_WHERE, new JSONObject());
      }
    }

    if (expirationTime != null) {
      parameters.put(KEY_EXPIRATION_TIME, expirationTime);
    } else if (expirationInterval != null) {
      parameters.put(KEY_EXPIRATION_INTERVAL, expirationInterval);
    }

    if (payload != null) {
      parameters.put(KEY_DATA, payload);
    }

    return new ParseRESTPushCommand("push", ParseHttpRequest.Method.POST, parameters, sessionToken);
  }
}

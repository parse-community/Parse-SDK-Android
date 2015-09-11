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

import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** package */ class ParseRESTQueryCommand extends ParseRESTCommand {

  public static <T extends ParseObject> ParseRESTQueryCommand findCommand(
      ParseQuery.State<T> state, String sessionToken) {
    String httpPath = String.format("classes/%s", state.className());
    Map <String, String> parameters = encode(state, false);
    return new ParseRESTQueryCommand(
        httpPath, ParseHttpRequest.Method.GET, parameters, sessionToken);
  }

  public static <T extends ParseObject> ParseRESTQueryCommand countCommand(
      ParseQuery.State<T> state, String sessionToken) {
    String httpPath = String.format("classes/%s", state.className());
    Map <String, String> parameters = encode(state, true);
    return new ParseRESTQueryCommand(
        httpPath, ParseHttpRequest.Method.GET, parameters, sessionToken);
  }

  /* package */ static <T extends ParseObject> Map<String, String> encode(
      ParseQuery.State<T> state, boolean count) {
    ParseEncoder encoder = PointerEncoder.get();
    HashMap<String, String> parameters = new HashMap<>();
    List<String> order = state.order();
    if (!order.isEmpty()) {
      parameters.put("order", ParseTextUtils.join(",", order));
    }

    ParseQuery.QueryConstraints conditions = state.constraints();
    if (!conditions.isEmpty()) {
      JSONObject encodedConditions =
          (JSONObject) encoder.encode(conditions);
      parameters.put("where", encodedConditions.toString());
    }

    // This is nullable since we allow unset selectedKeys as well as no selectedKeys
    Set<String> selectedKeys = state.selectedKeys();
    if (selectedKeys != null) {
      parameters.put("keys", ParseTextUtils.join(",", selectedKeys));
    }

    Set<String> includeds = state.includes();
    if (!includeds.isEmpty()) {
      parameters.put("include", ParseTextUtils.join(",", includeds));
    }

    if (count) {
      parameters.put("count", Integer.toString(1));
    } else {
      int limit = state.limit();
      if (limit >= 0) {
        parameters.put("limit", Integer.toString(limit));
      }

      int skip = state.skip();
      if (skip > 0) {
        parameters.put("skip", Integer.toString(skip));
      }
    }

    Map<String, Object> extraOptions = state.extraOptions();
    for (Map.Entry<String, Object> entry : extraOptions.entrySet()) {
      Object encodedExtraOptions = encoder.encode(entry.getValue());
      parameters.put(entry.getKey(), encodedExtraOptions.toString());
    }

    if (state.isTracingEnabled()) {
      parameters.put("trace", Integer.toString(1));
    }
    return parameters;
  }

  private ParseRESTQueryCommand(
      String httpPath,
      ParseHttpRequest.Method httpMethod,
      Map<String, ?> parameters,
      String sessionToken) {
    super(httpPath, httpMethod, parameters, sessionToken);
  }
}

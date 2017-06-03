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

  /* package */ final static String KEY_ORDER = "order";
  /* package */ final static String KEY_WHERE = "where";
  /* package */ final static String KEY_KEYS = "keys";
  /* package */ final static String KEY_INCLUDE = "include";
  /* package */ final static String KEY_LIMIT = "limit";
  /* package */ final static String KEY_COUNT = "count";
  /* package */ final static String KEY_SKIP = "skip";
  /* package */ final static String KEY_TRACE = "trace";

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
      parameters.put(KEY_ORDER, ParseTextUtils.join(",", order));
    }

    ParseQuery.QueryConstraints conditions = state.constraints();
    if (!conditions.isEmpty()) {
      JSONObject encodedConditions =
          (JSONObject) encoder.encode(conditions);
      parameters.put(KEY_WHERE, encodedConditions.toString());
    }

    // This is nullable since we allow unset selectedKeys as well as no selectedKeys
    Set<String> selectedKeys = state.selectedKeys();
    if (selectedKeys != null) {
      parameters.put(KEY_KEYS, ParseTextUtils.join(",", selectedKeys));
    }

    Set<String> includeds = state.includes();
    if (!includeds.isEmpty()) {
      parameters.put(KEY_INCLUDE, ParseTextUtils.join(",", includeds));
    }

    // Respect what the caller wanted for limit, even if count is true, because
    // parse-server supports it. Currently with our APIs, when counting, limit will always be 0,
    // but that logic is in ParseQuery class and we should not do that again here.
    int limit = state.limit();
    if (limit >= 0) {
      parameters.put(KEY_LIMIT, Integer.toString(limit));
    }

    if (count) {
      parameters.put(KEY_COUNT, Integer.toString(1));
    } else {
      int skip = state.skip();
      if (skip > 0) {
        parameters.put(KEY_SKIP, Integer.toString(skip));
      }
    }

    Map<String, Object> extraOptions = state.extraOptions();
    for (Map.Entry<String, Object> entry : extraOptions.entrySet()) {
      Object encodedExtraOptions = encoder.encode(entry.getValue());
      parameters.put(entry.getKey(), encodedExtraOptions.toString());
    }

    if (state.isTracingEnabled()) {
      parameters.put(KEY_TRACE, Integer.toString(1));
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

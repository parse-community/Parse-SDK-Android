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

import java.util.HashMap;
import java.util.Map;

/** package */ class ParseRESTConfigCommand extends ParseRESTCommand {

  public ParseRESTConfigCommand(
      String httpPath,
      ParseHttpRequest.Method httpMethod,
      Map<String, ?> parameters,
      String sessionToken) {
    super(httpPath, httpMethod, parameters, sessionToken);
  }

  public static ParseRESTConfigCommand fetchConfigCommand(String sessionToken) {
    return new ParseRESTConfigCommand("config", ParseHttpRequest.Method.GET, null, sessionToken);
  }

  public static ParseRESTConfigCommand updateConfigCommand(
      final Map<String, ?> configParameters, String sessionToken) {
    Map<String, Map<String, ?>> commandParameters = null;
    if (configParameters != null) {
      commandParameters = new HashMap<>();
      commandParameters.put("params", configParameters);
    }
    return new ParseRESTConfigCommand(
        "config", ParseHttpRequest.Method.PUT, commandParameters, sessionToken);
  }
}

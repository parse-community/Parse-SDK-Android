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

class ParseRESTSessionCommand extends ParseRESTCommand {

    private ParseRESTSessionCommand(
            String httpPath,
            ParseHttpRequest.Method httpMethod,
            JSONObject jsonParameters,
            String sessionToken) {
        super(httpPath, httpMethod, jsonParameters, sessionToken);
    }

    public static ParseRESTSessionCommand getCurrentSessionCommand(String sessionToken) {
        return new ParseRESTSessionCommand(
                "sessions/me", ParseHttpRequest.Method.GET, null, sessionToken);
    }

    public static ParseRESTSessionCommand revoke(String sessionToken) {
        return new ParseRESTSessionCommand(
                "logout", ParseHttpRequest.Method.POST, new JSONObject(), sessionToken);
    }

    public static ParseRESTSessionCommand upgradeToRevocableSessionCommand(String sessionToken) {
        return new ParseRESTSessionCommand(
                "upgradeToRevocableSession", ParseHttpRequest.Method.POST, new JSONObject(), sessionToken);
    }
}

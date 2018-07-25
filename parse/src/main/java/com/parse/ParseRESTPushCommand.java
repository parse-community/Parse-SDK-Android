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

import java.util.Set;

class ParseRESTPushCommand extends ParseRESTCommand {

    /* package */ final static String KEY_CHANNELS = "channels";
    /* package */ final static String KEY_WHERE = "where";
    /* package */ final static String KEY_DEVICE_TYPE = "deviceType";
    /* package */ final static String KEY_EXPIRATION_TIME = "expiration_time";
    /* package */ final static String KEY_EXPIRATION_INTERVAL = "expiration_interval";
    /* package */ final static String KEY_PUSH_TIME = "push_time";
    /* package */ final static String KEY_DATA = "data";

    public ParseRESTPushCommand(
            String httpPath,
            ParseHttpRequest.Method httpMethod,
            JSONObject parameters,
            String sessionToken) {
        super(httpPath, httpMethod, parameters, sessionToken);
    }

    public static ParseRESTPushCommand sendPushCommand(ParseQuery.State<ParseInstallation> query,
                                                       Set<String> targetChannels, Long expirationTime,
                                                       Long expirationInterval, Long pushTime, JSONObject payload, String sessionToken) {
        JSONObject parameters = new JSONObject();
        try {
            if (targetChannels != null) {
                parameters.put(KEY_CHANNELS, new JSONArray(targetChannels));
            } else {
                JSONObject whereJSON = null;
                if (query != null) {
                    ParseQuery.QueryConstraints where = query.constraints();
                    whereJSON = (JSONObject) PointerEncoder.get().encode(where);
                }
                if (whereJSON == null) {
                    // If there are no conditions set, then push to everyone by specifying empty query conditions.
                    whereJSON = new JSONObject();
                }
                parameters.put(KEY_WHERE, whereJSON);
            }

            if (expirationTime != null) {
                parameters.put(KEY_EXPIRATION_TIME, expirationTime);
            } else if (expirationInterval != null) {
                parameters.put(KEY_EXPIRATION_INTERVAL, expirationInterval);
            }

            if (pushTime != null) {
                parameters.put(KEY_PUSH_TIME, pushTime);
            }

            if (payload != null) {
                parameters.put(KEY_DATA, payload);
            }

        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        return new ParseRESTPushCommand("push", ParseHttpRequest.Method.POST, parameters, sessionToken);
    }
}

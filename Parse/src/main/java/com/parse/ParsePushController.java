/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import bolts.Task;

/** package */ class ParsePushController {

  /* package for test */ final static String DEVICE_TYPE_IOS = "ios";
  /* package for test */ final static String DEVICE_TYPE_ANDROID = "android";
  private final ParseHttpClient restClient;

  public ParsePushController(ParseHttpClient restClient) {
    this.restClient = restClient;
  }

  public Task<Void> sendInBackground(ParsePush.State state, String sessionToken) {
    return buildRESTSendPushCommand(state, sessionToken).executeAsync(restClient).makeVoid();
  }

  /* package for test */ ParseRESTCommand buildRESTSendPushCommand(ParsePush.State state,
      String sessionToken) {
    // pushToAndroid & pushToIOS are deprecated. It's OK to err on the side of omitting
    // a type constraint because the pusher will do a query rewrite on the backend to
    // constraint the query to supported device types only.
    String deviceType = null;
    if (state.queryState() == null) {
      // android is on by default, ios is off by default
      boolean willPushToAndroid = state.pushToAndroid() != null && state.pushToAndroid();
      boolean willPushToIOS = state.pushToIOS() != null && state.pushToIOS();
      if (willPushToIOS && willPushToAndroid) {
        // Push to both by not including a 'type' parameter
      } else if (willPushToIOS) {
        deviceType = DEVICE_TYPE_IOS;
      } else if (willPushToAndroid) {
        deviceType = DEVICE_TYPE_ANDROID;
      }
    }
    return ParseRESTPushCommand.sendPushCommand(state.queryState(), state.channelSet(), deviceType,
        state.expirationTime(), state.expirationTimeInterval(), state.pushTime(), state.data(),
        sessionToken);
  }
}

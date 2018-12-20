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

class ParsePushController {

    private final ParseHttpClient restClient;

    public ParsePushController(ParseHttpClient restClient) {
        this.restClient = restClient;
    }

    public Task<Void> sendInBackground(ParsePush.State state, String sessionToken) {
        return buildRESTSendPushCommand(state, sessionToken).executeAsync(restClient).makeVoid();
    }

    ParseRESTCommand buildRESTSendPushCommand(ParsePush.State state,
                                                                     String sessionToken) {
        return ParseRESTPushCommand.sendPushCommand(state.queryState(), state.channelSet(),
                state.expirationTime(), state.expirationTimeInterval(), state.pushTime(), state.data(),
                sessionToken);
    }
}

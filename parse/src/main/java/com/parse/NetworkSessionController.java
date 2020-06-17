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

import com.parse.boltsinternal.Continuation;
import com.parse.boltsinternal.Task;

class NetworkSessionController implements ParseSessionController {

    private final ParseHttpClient client;
    private final ParseObjectCoder coder;

    public NetworkSessionController(ParseHttpClient client) {
        this.client = client;
        this.coder = ParseObjectCoder.get(); // TODO(grantland): Inject
    }

    @Override
    public Task<ParseObject.State> getSessionAsync(String sessionToken) {
        ParseRESTSessionCommand command =
                ParseRESTSessionCommand.getCurrentSessionCommand(sessionToken);

        return command.executeAsync(client).onSuccess(new Continuation<JSONObject, ParseObject.State>() {
            @Override
            public ParseObject.State then(Task<JSONObject> task) {
                JSONObject result = task.getResult();
                return coder.decode(new ParseObject.State.Builder("_Session"), result, ParseDecoder.get())
                        .isComplete(true)
                        .build();
            }
        });
    }

    @Override
    public Task<Void> revokeAsync(String sessionToken) {
        return ParseRESTSessionCommand.revoke(sessionToken)
                .executeAsync(client)
                .makeVoid();
    }

    @Override
    public Task<ParseObject.State> upgradeToRevocable(String sessionToken) {
        ParseRESTSessionCommand command =
                ParseRESTSessionCommand.upgradeToRevocableSessionCommand(sessionToken);
        return command.executeAsync(client).onSuccess(new Continuation<JSONObject, ParseObject.State>() {
            @Override
            public ParseObject.State then(Task<JSONObject> task) {
                JSONObject result = task.getResult();
                return coder.decode(new ParseObject.State.Builder("_Session"), result, ParseDecoder.get())
                        .isComplete(true)
                        .build();
            }
        });
    }
}

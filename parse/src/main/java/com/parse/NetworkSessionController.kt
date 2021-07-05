/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse

import com.parse.ParseObjectCoder.Companion.get
import com.parse.boltsinternal.Task
import org.json.JSONObject

internal class NetworkSessionController(private val client: ParseHttpClient) :
    ParseSessionController {
    private val coder: ParseObjectCoder = get()

    override fun getSessionAsync(sessionToken: String): Task<ParseObject.State> {
        val command = ParseRESTSessionCommand.getCurrentSessionCommand(sessionToken)
        return command.executeAsync(client).onSuccess { task: Task<JSONObject?> ->
            val result = task.result
            coder.decode(ParseObject.State.Builder("_Session"), result!!, ParseDecoder.get())
                .isComplete(true)
                .build()
        }
    }

    override fun revokeAsync(sessionToken: String): Task<Void> {
        return ParseRESTSessionCommand.revoke(sessionToken)
            .executeAsync(client)
            .makeVoid()
    }

    override fun upgradeToRevocable(sessionToken: String): Task<ParseObject.State> {
        val command = ParseRESTSessionCommand.upgradeToRevocableSessionCommand(sessionToken)
        return command.executeAsync(client).onSuccess { task: Task<JSONObject?> ->
            val result = task.result
            coder.decode(ParseObject.State.Builder("_Session"), result!!, ParseDecoder.get())
                .isComplete(true)
                .build()
        }
    }

    init {
        // TODO(grantland): Inject
    }
}
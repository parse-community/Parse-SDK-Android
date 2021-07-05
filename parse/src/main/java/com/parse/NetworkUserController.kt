/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse

import com.parse.ParseRESTUserCommand
import com.parse.boltsinternal.Task
import org.json.JSONObject

internal class NetworkUserController @JvmOverloads constructor(
    private val client: ParseHttpClient,
    private val revocableSession: Boolean = false
) : ParseUserController {
    private val coder: ParseObjectCoder = ParseObjectCoder.get()

    override fun signUpAsync(
        state: ParseObject.State,
        operations: ParseOperationSet,
        sessionToken: String
    ): Task<ParseUser.State> {
        val objectJSON = coder.encode(state, operations, PointerEncoder.get())
        val command: ParseRESTCommand = ParseRESTUserCommand.signUpUserCommand(
            objectJSON, sessionToken, revocableSession
        )
        return command.executeAsync(client).onSuccess { task: Task<JSONObject?> ->
            val result = task.result
            coder.decode(ParseUser.State.Builder(), result!!, ParseDecoder.get())
                .isComplete(false)
                .isNew(true)
                .build()
        }
    }

    //region logInAsync
    override fun logInAsync(
        username: String, password: String
    ): Task<ParseUser.State> {
        val command: ParseRESTCommand = ParseRESTUserCommand.logInUserCommand(
            username, password, revocableSession
        )
        return command.executeAsync(client).onSuccess { task: Task<JSONObject?> ->
            val result = task.result
            coder.decode(ParseUser.State.Builder(), result!!, ParseDecoder.get())
                .isComplete(true)
                .build()
        }
    }

    override fun logInAsync(
        state: ParseUser.State, operations: ParseOperationSet
    ): Task<ParseUser.State> {
        val objectJSON = coder.encode(state, operations, PointerEncoder.get())
        val command = ParseRESTUserCommand.serviceLogInUserCommand(
            objectJSON, state.sessionToken(), revocableSession
        )
        return command.executeAsync(client).onSuccess { task: Task<JSONObject?> ->
            val result = task.result

            // TODO(grantland): Does the server really respond back with complete object data if the
            // object isn't new?
            val isNew = command.statusCode == STATUS_CODE_CREATED
            val isComplete = !isNew
            coder.decode(ParseUser.State.Builder(), result!!, ParseDecoder.get())
                .isComplete(isComplete)
                .isNew(isNew)
                .build()
        }
    }

    override fun logInAsync(
        authType: String, authData: Map<String, String>
    ): Task<ParseUser.State> {
        val command = ParseRESTUserCommand.serviceLogInUserCommand(
            authType, authData, revocableSession
        )
        return command.executeAsync(client).onSuccess { task: Task<JSONObject?> ->
            val result = task.result
            coder.decode(ParseUser.State.Builder(), result!!, ParseDecoder.get())
                .isComplete(true)
                .isNew(command.statusCode == STATUS_CODE_CREATED)
                .putAuthData(authType, authData)
                .build()
        }
    }

    //endregion
    override fun getUserAsync(sessionToken: String): Task<ParseUser.State> {
        val command: ParseRESTCommand = ParseRESTUserCommand.getCurrentUserCommand(sessionToken)
        return command.executeAsync(client).onSuccess { task: Task<JSONObject?> ->
            val result = task.result
            coder.decode(ParseUser.State.Builder(), result!!, ParseDecoder.get())
                .isComplete(true)
                .build()
        }
    }

    override fun requestPasswordResetAsync(email: String): Task<Void> {
        val command: ParseRESTCommand = ParseRESTUserCommand.resetPasswordResetCommand(email)
        return command.executeAsync(client).makeVoid()
    }

    companion object {
        private const val STATUS_CODE_CREATED = 201
    }
}
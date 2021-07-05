/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse

import com.parse.ParseRESTObjectBatchCommand.Companion.executeBatch
import com.parse.boltsinternal.Task
import org.json.JSONObject
import java.util.*

internal class NetworkObjectController(private val client: ParseHttpClient) :
    ParseObjectController {
    private val coder: ParseObjectCoder = ParseObjectCoder.get()

    override fun fetchAsync(
        state: ParseObject.State, sessionToken: String, decoder: ParseDecoder
    ): Task<ParseObject.State> {
        val command: ParseRESTCommand = ParseRESTObjectCommand.getObjectCommand(
            state.objectId(),
            state.className(),
            sessionToken
        )
        return command.executeAsync(client).onSuccess { task: Task<JSONObject?> ->
            val result = task.result
            // Copy and clear to create an new empty instance of the same type as `state`
            val builder = state.newBuilder<ParseObject.State.Init<*>>().clear()
            coder.decode(builder, result!!, decoder)
                .isComplete(true)
                .build()
        }
    }

    override fun saveAsync(
        state: ParseObject.State,
        operations: ParseOperationSet,
        sessionToken: String,
        decoder: ParseDecoder
    ): Task<ParseObject.State> {
        /*
         * Get the JSON representation of the object, and use some of the information to construct the
         * command.
         */
        val objectJSON = coder.encode(state, operations, PointerEncoder.get())
        val command = ParseRESTObjectCommand.saveObjectCommand(
            state,
            objectJSON,
            sessionToken
        )
        return command.executeAsync(client).onSuccess { task: Task<JSONObject?> ->
            val result = task.result
            // Copy and clear to create an new empty instance of the same type as `state`
            val builder = state.newBuilder<ParseObject.State.Init<*>>().clear()
            coder.decode(builder, result!!, decoder)
                .isComplete(false)
                .build()
        }
    }

    override fun saveAllAsync(
        states: List<ParseObject.State>,
        operationsList: List<ParseOperationSet>,
        sessionToken: String,
        decoders: List<ParseDecoder>
    ): List<Task<ParseObject.State>> {
        val batchSize = states.size
        val commands: MutableList<ParseRESTObjectCommand> = ArrayList(batchSize)
        val encoder: ParseEncoder = PointerEncoder.get()
        for (i in 0 until batchSize) {
            val state = states[i]
            val operations = operationsList[i]
            val objectJSON = coder.encode(state, operations, encoder)
            val command = ParseRESTObjectCommand.saveObjectCommand(
                state, objectJSON, sessionToken
            )
            commands.add(command)
        }
        val batchTasks = executeBatch(
            client, commands, sessionToken
        )
        val tasks: MutableList<Task<ParseObject.State>> = ArrayList(batchSize)
        for (i in 0 until batchSize) {
            val state = states[i]
            val decoder = decoders[i]
            tasks.add(batchTasks[i].onSuccess { task: Task<JSONObject?> ->
                val result = task.result
                // Copy and clear to create an new empty instance of the same type as `state`
                val builder = state.newBuilder<ParseObject.State.Init<*>>().clear()
                coder.decode(builder, result!!, decoder)
                    .isComplete(false)
                    .build()
            })
        }
        return tasks
    }

    override fun deleteAsync(state: ParseObject.State, sessionToken: String): Task<Void> {
        val command = ParseRESTObjectCommand.deleteObjectCommand(
            state, sessionToken
        )
        return command.executeAsync(client).makeVoid()
    }

    override fun deleteAllAsync(
        states: List<ParseObject.State>, sessionToken: String
    ): List<Task<Void>> {
        val batchSize = states.size
        val commands: MutableList<ParseRESTObjectCommand> = ArrayList(batchSize)
        for (i in 0 until batchSize) {
            val state = states[i]
            val command = ParseRESTObjectCommand.deleteObjectCommand(
                state, sessionToken
            )
            commands.add(command)
        }
        val batchTasks = executeBatch(
            client, commands, sessionToken
        )
        val tasks: MutableList<Task<Void>> = ArrayList(batchSize)
        for (i in 0 until batchSize) {
            tasks.add(batchTasks[i].makeVoid())
        }
        return tasks
    }

}
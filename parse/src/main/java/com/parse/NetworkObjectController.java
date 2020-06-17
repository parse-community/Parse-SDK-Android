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

import java.util.ArrayList;
import java.util.List;

import com.parse.boltsinternal.Continuation;
import com.parse.boltsinternal.Task;

class NetworkObjectController implements ParseObjectController {

    private ParseHttpClient client;
    private ParseObjectCoder coder;

    public NetworkObjectController(ParseHttpClient client) {
        this.client = client;
        this.coder = ParseObjectCoder.get();
    }

    @Override
    public Task<ParseObject.State> fetchAsync(
            final ParseObject.State state, String sessionToken, final ParseDecoder decoder) {
        final ParseRESTCommand command = ParseRESTObjectCommand.getObjectCommand(
                state.objectId(),
                state.className(),
                sessionToken);

        return command.executeAsync(client).onSuccess(new Continuation<JSONObject, ParseObject.State>() {
            @Override
            public ParseObject.State then(Task<JSONObject> task) {
                JSONObject result = task.getResult();
                // Copy and clear to create an new empty instance of the same type as `state`
                ParseObject.State.Init<?> builder = state.newBuilder().clear();
                return coder.decode(builder, result, decoder)
                        .isComplete(true)
                        .build();
            }
        });
    }

    @Override
    public Task<ParseObject.State> saveAsync(
            final ParseObject.State state,
            final ParseOperationSet operations,
            String sessionToken,
            final ParseDecoder decoder) {
        /*
         * Get the JSON representation of the object, and use some of the information to construct the
         * command.
         */
        JSONObject objectJSON = coder.encode(state, operations, PointerEncoder.get());

        ParseRESTObjectCommand command = ParseRESTObjectCommand.saveObjectCommand(
                state,
                objectJSON,
                sessionToken);
        return command.executeAsync(client).onSuccess(new Continuation<JSONObject, ParseObject.State>() {
            @Override
            public ParseObject.State then(Task<JSONObject> task) {
                JSONObject result = task.getResult();
                // Copy and clear to create an new empty instance of the same type as `state`
                ParseObject.State.Init<?> builder = state.newBuilder().clear();
                return coder.decode(builder, result, decoder)
                        .isComplete(false)
                        .build();
            }
        });
    }

    @Override
    public List<Task<ParseObject.State>> saveAllAsync(
            List<ParseObject.State> states,
            List<ParseOperationSet> operationsList,
            String sessionToken,
            List<ParseDecoder> decoders) {
        int batchSize = states.size();

        List<ParseRESTObjectCommand> commands = new ArrayList<>(batchSize);
        ParseEncoder encoder = PointerEncoder.get();
        for (int i = 0; i < batchSize; i++) {
            ParseObject.State state = states.get(i);
            ParseOperationSet operations = operationsList.get(i);
            JSONObject objectJSON = coder.encode(state, operations, encoder);

            ParseRESTObjectCommand command = ParseRESTObjectCommand.saveObjectCommand(
                    state, objectJSON, sessionToken);
            commands.add(command);
        }

        final List<Task<JSONObject>> batchTasks =
                ParseRESTObjectBatchCommand.executeBatch(client, commands, sessionToken);

        final List<Task<ParseObject.State>> tasks = new ArrayList<>(batchSize);
        for (int i = 0; i < batchSize; i++) {
            final ParseObject.State state = states.get(i);
            final ParseDecoder decoder = decoders.get(i);
            tasks.add(batchTasks.get(i).onSuccess(new Continuation<JSONObject, ParseObject.State>() {
                @Override
                public ParseObject.State then(Task<JSONObject> task) {
                    JSONObject result = task.getResult();
                    // Copy and clear to create an new empty instance of the same type as `state`
                    ParseObject.State.Init<?> builder = state.newBuilder().clear();
                    return coder.decode(builder, result, decoder)
                            .isComplete(false)
                            .build();
                }
            }));
        }
        return tasks;
    }

    @Override
    public Task<Void> deleteAsync(ParseObject.State state, String sessionToken) {
        ParseRESTObjectCommand command = ParseRESTObjectCommand.deleteObjectCommand(
                state, sessionToken);

        return command.executeAsync(client).makeVoid();
    }

    @Override
    public List<Task<Void>> deleteAllAsync(
            List<ParseObject.State> states, String sessionToken) {
        int batchSize = states.size();

        List<ParseRESTObjectCommand> commands = new ArrayList<>(batchSize);
        for (int i = 0; i < batchSize; i++) {
            ParseObject.State state = states.get(i);
            ParseRESTObjectCommand command = ParseRESTObjectCommand.deleteObjectCommand(
                    state, sessionToken);
            commands.add(command);
        }

        final List<Task<JSONObject>> batchTasks =
                ParseRESTObjectBatchCommand.executeBatch(client, commands, sessionToken);

        List<Task<Void>> tasks = new ArrayList<>(batchSize);
        for (int i = 0; i < batchSize; i++) {
            tasks.add(batchTasks.get(i).makeVoid());
        }
        return tasks;
    }
}

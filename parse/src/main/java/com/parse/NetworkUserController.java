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

import java.util.Map;

import bolts.Continuation;
import bolts.Task;

/** package */ class NetworkUserController implements ParseUserController {

  private static final int STATUS_CODE_CREATED = 201;

  private final ParseHttpClient client;
  private final ParseObjectCoder coder;
  private final boolean revocableSession;

  public NetworkUserController(ParseHttpClient client) {
    this(client, false);
  }

  public NetworkUserController(ParseHttpClient client, boolean revocableSession) {
    this.client = client;
    this.coder = ParseObjectCoder.get(); // TODO(grantland): Inject
    this.revocableSession = revocableSession;
  }

  @Override
  public Task<ParseUser.State> signUpAsync(
      final ParseObject.State state,
      ParseOperationSet operations,
      String sessionToken) {
    JSONObject objectJSON = coder.encode(state, operations, PointerEncoder.get());
    ParseRESTCommand command = ParseRESTUserCommand.signUpUserCommand(
        objectJSON, sessionToken, revocableSession);

    return command.executeAsync(client).onSuccess(new Continuation<JSONObject, ParseUser.State>() {
      @Override
      public ParseUser.State then(Task<JSONObject> task) throws Exception {
        JSONObject result = task.getResult();
        return coder.decode(new ParseUser.State.Builder(), result, ParseDecoder.get())
            .isComplete(false)
            .isNew(true)
            .build();
      }
    });
  }

  //region logInAsync

  @Override
  public Task<ParseUser.State> logInAsync(
      String username, String password) {
    ParseRESTCommand command = ParseRESTUserCommand.logInUserCommand(
        username, password, revocableSession);
    return command.executeAsync(client).onSuccess(new Continuation<JSONObject, ParseUser.State>() {
      @Override
      public ParseUser.State then(Task<JSONObject> task) throws Exception {
        JSONObject result = task.getResult();

        return coder.decode(new ParseUser.State.Builder(), result, ParseDecoder.get())
            .isComplete(true)
            .build();
      }
    });
  }

  @Override
  public Task<ParseUser.State> logInAsync(
      ParseUser.State state, ParseOperationSet operations) {
    JSONObject objectJSON = coder.encode(state, operations, PointerEncoder.get());
    final ParseRESTUserCommand command = ParseRESTUserCommand.serviceLogInUserCommand(
        objectJSON, state.sessionToken(), revocableSession);

    return command.executeAsync(client).onSuccess(new Continuation<JSONObject, ParseUser.State>() {
      @Override
      public ParseUser.State then(Task<JSONObject> task) throws Exception {
        JSONObject result = task.getResult();

        // TODO(grantland): Does the server really respond back with complete object data if the
        // object isn't new?
        boolean isNew = command.getStatusCode() == STATUS_CODE_CREATED;
        boolean isComplete = !isNew;

        return coder.decode(new ParseUser.State.Builder(), result, ParseDecoder.get())
            .isComplete(isComplete)
            .isNew(isNew)
            .build();
      }
    });
  }

  @Override
  public Task<ParseUser.State> logInAsync(
      final String authType, final Map<String, String> authData) {
    final ParseRESTUserCommand command = ParseRESTUserCommand.serviceLogInUserCommand(
        authType, authData, revocableSession);
    return command.executeAsync(client).onSuccess(new Continuation<JSONObject, ParseUser.State>() {
      @Override
      public ParseUser.State then(Task<JSONObject> task) throws Exception {
        JSONObject result = task.getResult();

        return coder.decode(new ParseUser.State.Builder(), result, ParseDecoder.get())
            .isComplete(true)
            .isNew(command.getStatusCode() == STATUS_CODE_CREATED)
            .putAuthData(authType, authData)
            .build();
      }
    });
  }

  //endregion

  @Override
  public Task<ParseUser.State> getUserAsync(String sessionToken) {
    ParseRESTCommand command = ParseRESTUserCommand.getCurrentUserCommand(sessionToken);
    return command.executeAsync(client).onSuccess(new Continuation<JSONObject, ParseUser.State>() {
      @Override
      public ParseUser.State then(Task<JSONObject> task) throws Exception {
        JSONObject result = task.getResult();

        return coder.decode(new ParseUser.State.Builder(), result, ParseDecoder.get())
            .isComplete(true)
            .build();
      }
    });
  }

  @Override
  public Task<Void> requestPasswordResetAsync(String email) {
    ParseRESTCommand command = ParseRESTUserCommand.resetPasswordResetCommand(email);
    return command.executeAsync(client).makeVoid();
  }
}

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

import bolts.Continuation;
import bolts.Task;

/** package */ class ParseConfigController {

  private ParseCurrentConfigController currentConfigController;
  private final ParseHttpClient restClient;

  public ParseConfigController(ParseHttpClient restClient,
      ParseCurrentConfigController currentConfigController) {
    this.restClient = restClient;
    this.currentConfigController = currentConfigController;
  }
  /* package */ ParseCurrentConfigController getCurrentConfigController() {
    return currentConfigController;
  }

  public Task<ParseConfig> getAsync(String sessionToken) {
    final ParseRESTCommand command = ParseRESTConfigCommand.fetchConfigCommand(sessionToken);
    command.enableRetrying();
    return command.executeAsync(restClient).onSuccessTask(new Continuation<JSONObject, Task<ParseConfig>>() {
      @Override
      public Task<ParseConfig> then(Task<JSONObject> task) throws Exception {
        JSONObject result = task.getResult();

        final ParseConfig config = ParseConfig.decode(result, ParseDecoder.get());
        return currentConfigController.setCurrentConfigAsync(config).continueWith(new Continuation<Void, ParseConfig>() {
          @Override
          public ParseConfig then(Task<Void> task) throws Exception {
            return config;
          }
        });
      }
    });
  }
}

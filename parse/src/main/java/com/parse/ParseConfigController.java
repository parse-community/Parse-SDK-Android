/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import com.parse.boltsinternal.Task;
import org.json.JSONObject;

class ParseConfigController {

    private final ParseHttpClient restClient;
    private final ParseCurrentConfigController currentConfigController;

    public ParseConfigController(
            ParseHttpClient restClient, ParseCurrentConfigController currentConfigController) {
        this.restClient = restClient;
        this.currentConfigController = currentConfigController;
    }

    /* package */ ParseCurrentConfigController getCurrentConfigController() {
        return currentConfigController;
    }

    public Task<ParseConfig> getAsync(String sessionToken) {
        final ParseRESTCommand command = ParseRESTConfigCommand.fetchConfigCommand(sessionToken);
        return command.executeAsync(restClient)
                .onSuccessTask(
                        task -> {
                            JSONObject result = task.getResult();

                            final ParseConfig config =
                                    ParseConfig.decode(result, ParseDecoder.get());
                            return currentConfigController
                                    .setCurrentConfigAsync(config)
                                    .continueWith(task1 -> config);
                        });
    }
}

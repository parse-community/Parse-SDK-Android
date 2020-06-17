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

import com.parse.boltsinternal.Continuation;
import com.parse.boltsinternal.Task;

class ParseCloudCodeController {

    /* package for test */ final ParseHttpClient restClient;

    public ParseCloudCodeController(ParseHttpClient restClient) {
        this.restClient = restClient;
    }

    public <T> Task<T> callFunctionInBackground(final String name,
                                                final Map<String, ?> params, String sessionToken) {
        ParseRESTCommand command = ParseRESTCloudCommand.callFunctionCommand(
                name,
                params,
                sessionToken);
        return command.executeAsync(restClient).onSuccess(new Continuation<JSONObject, T>() {
            @Override
            public T then(Task<JSONObject> task) {
                @SuppressWarnings("unchecked")
                T result = (T) convertCloudResponse(task.getResult());
                return result;
            }
        });
    }

    /*
     * Decodes any Parse data types in the result of the cloud function call.
     */
    /* package for test */ Object convertCloudResponse(Object result) {
        if (result instanceof JSONObject) {
            JSONObject jsonResult = (JSONObject) result;
            // We want to make sure we pass back a null result as null, and not a JSONObject
            if (jsonResult.isNull("result")) {
                return null;
            }
            result = jsonResult.opt("result");
        }

        ParseDecoder decoder = ParseDecoder.get();
        Object finalResult = decoder.decode(result);
        if (finalResult != null) {
            return finalResult;
        }

        return result;
    }
}

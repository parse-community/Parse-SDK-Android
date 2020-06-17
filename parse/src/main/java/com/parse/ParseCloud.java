/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import androidx.annotation.NonNull;

import java.util.List;
import java.util.Map;

import com.parse.boltsinternal.Continuation;
import com.parse.boltsinternal.Task;

/**
 * The ParseCloud class defines provides methods for interacting with Parse Cloud Functions. A Cloud
 * Function can be called with {@link #callFunctionInBackground(String, Map, FunctionCallback)}
 * using a {@link FunctionCallback}. For example, this sample code calls the "validateGame" Cloud
 * Function and calls processResponse if the call succeeded and handleError if it failed.
 * <p>
 * <pre>
 * ParseCloud.callFunctionInBackground("validateGame", parameters, new FunctionCallback<Object>() {
 *      public void done(Object object, ParseException e) {
 *        if (e == null) {
 *          processResponse(object);
 *        } else {
 *          handleError();
 *        }
 *      }
 * }
 * </pre>
 * <p>
 * Using the callback methods is usually preferred because the network operation will not block the
 * calling thread. However, in some cases it may be easier to use the
 * {@link #callFunction(String, Map)} call which do block the calling thread. For example, if your
 * application has already spawned a background task to perform work, that background task could use
 * the blocking calls and avoid the code complexity of callbacks.
 */
public final class ParseCloud {

    private ParseCloud() {
        // do nothing
    }

    /* package for test */
    static ParseCloudCodeController getCloudCodeController() {
        return ParseCorePlugins.getInstance().getCloudCodeController();
    }

    /**
     * Calls a cloud function in the background.
     *
     * @param name   The cloud function to call.
     * @param params The parameters to send to the cloud function. This map can contain anything that could
     *               be placed in a ParseObject except for ParseObjects themselves.
     * @return A Task that will be resolved when the cloud function has returned.
     */
    public static <T> Task<T> callFunctionInBackground(@NonNull final String name,
                                                       @NonNull final Map<String, ?> params) {
        return ParseUser.getCurrentSessionTokenAsync().onSuccessTask(new Continuation<String, Task<T>>() {
            @Override
            public Task<T> then(Task<String> task) {
                String sessionToken = task.getResult();
                return getCloudCodeController().callFunctionInBackground(name, params, sessionToken);
            }
        });
    }

    /**
     * Calls a cloud function.
     *
     * @param name   The cloud function to call.
     * @param params The parameters to send to the cloud function. This map can contain anything that could
     *               be placed in a ParseObject except for ParseObjects themselves.
     * @return The result of the cloud call. Result may be a @{link Map}&lt; {@link String}, ?&gt;,
     * {@link ParseObject}, {@link List}&lt;?&gt;, or any type that can be set as a field in a
     * ParseObject.
     * @throws ParseException exception
     */
    public static <T> T callFunction(@NonNull String name, @NonNull Map<String, ?> params) throws ParseException {
        return ParseTaskUtils.wait(ParseCloud.<T>callFunctionInBackground(name, params));
    }

    /**
     * Calls a cloud function in the background.
     *
     * @param name     The cloud function to call.
     * @param params   The parameters to send to the cloud function. This map can contain anything that could
     *                 be placed in a ParseObject except for ParseObjects themselves.
     * @param callback The callback that will be called when the cloud function has returned.
     */
    public static <T> void callFunctionInBackground(@NonNull String name, @NonNull Map<String, ?> params,
                                                    @NonNull FunctionCallback<T> callback) {
        ParseTaskUtils.callbackOnMainThreadAsync(
                ParseCloud.<T>callFunctionInBackground(name, params),
                callback);
    }
}

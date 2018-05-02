/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import com.parse.http.ParseHttpRequest;
import com.parse.http.ParseHttpResponse;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import bolts.Continuation;
import bolts.Task;
import bolts.TaskCompletionSource;

/** package */ class ParseRESTObjectBatchCommand extends ParseRESTCommand {
  public final static int COMMAND_OBJECT_BATCH_MAX_SIZE = 50;

  private static final String KEY_RESULTS = "results";

  public static List<Task<JSONObject>> executeBatch(
      ParseHttpClient client, List<ParseRESTObjectCommand> commands, String sessionToken) {
    final int batchSize = commands.size();
    List<Task<JSONObject>> tasks = new ArrayList<>(batchSize);

    if (batchSize == 1) {
      // There's only one, just execute it
      tasks.add(commands.get(0).executeAsync(client));
      return tasks;
    }

    if (batchSize > COMMAND_OBJECT_BATCH_MAX_SIZE) {
      // There's more than the max, split it up into batches
      List<List<ParseRESTObjectCommand>> batches = Lists.partition(commands,
          COMMAND_OBJECT_BATCH_MAX_SIZE);
      for (int i = 0, size = batches.size(); i < size; i++) {
        List<ParseRESTObjectCommand> batch = batches.get(i);
        tasks.addAll(executeBatch(client, batch, sessionToken));
      }
      return tasks;
    }

    final List<TaskCompletionSource<JSONObject>> tcss = new ArrayList<>(batchSize);
    for (int i = 0; i < batchSize; i++) {
      TaskCompletionSource<JSONObject> tcs = new TaskCompletionSource<>();
      tcss.add(tcs);
      tasks.add(tcs.getTask());
    }

    JSONObject parameters = new JSONObject();
    JSONArray requests = new JSONArray();
    try {
      for (ParseRESTObjectCommand command : commands) {
        JSONObject requestParameters = new JSONObject();
        requestParameters.put("method", command.method.toString());
        requestParameters.put("path", new URL(server, command.httpPath).getPath());
        JSONObject body = command.jsonParameters;
        if (body != null) {
          requestParameters.put("body", body);
        }
        requests.put(requestParameters);
      }
      parameters.put("requests", requests);
    } catch (JSONException | MalformedURLException e) {
      throw new RuntimeException(e);
    }

    ParseRESTCommand command = new ParseRESTObjectBatchCommand(
        "batch", ParseHttpRequest.Method.POST, parameters, sessionToken);

    command.executeAsync(client).continueWith(new Continuation<JSONObject, Void>() {
      @Override
      public Void then(Task<JSONObject> task) throws Exception {
        TaskCompletionSource<JSONObject> tcs;

        if (task.isFaulted() || task.isCancelled()) {
          // REST command failed or canceled, fail or cancel all tasks
          for (int i = 0; i < batchSize; i++) {
            tcs = tcss.get(i);
            if (task.isFaulted()) {
              tcs.setError(task.getError());
            } else {
              tcs.setCancelled();
            }
          }
        }

        JSONObject json = task.getResult();
        JSONArray results = json.getJSONArray(KEY_RESULTS);

        int resultLength = results.length();
        if (resultLength != batchSize) {
          // Invalid response, fail all tasks
          for (int i = 0; i < batchSize; i++) {
            tcs = tcss.get(i);
            tcs.setError(new IllegalStateException(
                "Batch command result count expected: " + batchSize + " but was: " + resultLength));
          }
        }

        for (int i = 0; i < batchSize; i++) {
          JSONObject result = results.getJSONObject(i);
          tcs = tcss.get(i);

          if (result.has("success")) {
            JSONObject success = result.getJSONObject("success");
            tcs.setResult(success);
          } else if (result.has("error")) {
            JSONObject error = result.getJSONObject("error");
            tcs.setError(new ParseException(error.getInt("code"), error.getString("error")));
          }
        }
        return null;
      }
    });

    return tasks;
  }

  private ParseRESTObjectBatchCommand(
      String httpPath,
      ParseHttpRequest.Method httpMethod,
      JSONObject parameters,
      String sessionToken) {
    super(httpPath, httpMethod, parameters, sessionToken);
  }

  /**
   * /batch is the only endpoint that doesn't return a JSONObject... It returns a JSONArray, but
   * let's wrap that with a JSONObject {@code { "results": &lt;original response%gt; }}.
   */
  @Override
  protected Task<JSONObject> onResponseAsync(ParseHttpResponse response,
      ProgressCallback downloadProgressCallback) {
    InputStream responseStream = null;
    String content = null;
    try {
      responseStream = response.getContent();
      content = new String(ParseIOUtils.toByteArray(responseStream));
    } catch (IOException e) {
      return Task.forError(e);
    } finally {
      ParseIOUtils.closeQuietly(responseStream);
    }

    JSONObject json;
    try {
      JSONArray results = new JSONArray(content);
      json = new JSONObject();
      json.put(KEY_RESULTS, results);
    } catch (JSONException e) {
      return Task.forError(newTemporaryException("bad json response", e));
    }

    return Task.forResult(json);
  }
}

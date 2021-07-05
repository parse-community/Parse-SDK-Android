/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse

import com.parse.boltsinternal.Continuation
import com.parse.boltsinternal.Task
import com.parse.boltsinternal.TaskCompletionSource
import com.parse.http.ParseHttpRequest
import com.parse.http.ParseHttpResponse
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.net.MalformedURLException
import java.net.URL
import java.util.*

internal class ParseRESTObjectBatchCommand private constructor(
    httpPath: String,
    httpMethod: ParseHttpRequest.Method,
    parameters: JSONObject,
    sessionToken: String?
) : ParseRESTCommand(httpPath, httpMethod, parameters, sessionToken) {
    /**
     * /batch is the only endpoint that doesn't return a JSONObject... It returns a JSONArray, but
     * let's wrap that with a JSONObject `{ "results": &lt;original response%gt; }`.
     */
    override fun onResponseAsync(
        response: ParseHttpResponse?,
        downloadProgressCallback: ProgressCallback?
    ): Task<JSONObject?>? {
        var responseStream: InputStream? = null
        val content: String
        try {
            responseStream = response!!.content
            content = String(ParseIOUtils.toByteArray(responseStream))
        } catch (e: IOException) {
            return Task.forError(e)
        } finally {
            ParseIOUtils.closeQuietly(responseStream)
        }
        val json: JSONObject
        try {
            val results = JSONArray(content)
            json = JSONObject()
            json.put(KEY_RESULTS, results)
        } catch (e: JSONException) {
            return Task.forError(newTemporaryException("bad json response", e))
        }
        return Task.forResult(json)
    }

    companion object {
        private const val COMMAND_OBJECT_BATCH_MAX_SIZE = 50
        private const val KEY_RESULTS = "results"
        @JvmStatic
        fun executeBatch(
            client: ParseHttpClient?, commands: List<ParseRESTObjectCommand>, sessionToken: String?
        ): List<Task<JSONObject?>> {
            val batchSize = commands.size
            val tasks: MutableList<Task<JSONObject?>> = ArrayList(batchSize)
            if (batchSize == 1) {
                // There's only one, just execute it
                tasks.add(commands[0].executeAsync(client!!))
                return tasks
            }
            if (batchSize > COMMAND_OBJECT_BATCH_MAX_SIZE) {
                // There's more than the max, split it up into batches
                val batches = commands.chunked(COMMAND_OBJECT_BATCH_MAX_SIZE)
                var i = 0
                val size = batches.size
                while (i < size) {
                    val batch = batches[i]
                    tasks.addAll(executeBatch(client, batch, sessionToken))
                    i++
                }
                return tasks
            }
            val tcss: MutableList<TaskCompletionSource<JSONObject?>> = ArrayList(batchSize)
            for (i in 0 until batchSize) {
                val tcs = TaskCompletionSource<JSONObject?>()
                tcss.add(tcs)
                tasks.add(tcs.task)
            }
            val parameters = JSONObject()
            val requests = JSONArray()
            try {
                for (command in commands) {
                    val requestParameters = JSONObject()
                    requestParameters.put("method", command.method.toString())
                    requestParameters.put("path", URL(server, command.httpPath).path)
                    val body = command.jsonParameters
                    if (body != null) {
                        requestParameters.put("body", body)
                    }
                    requests.put(requestParameters)
                }
                parameters.put("requests", requests)
            } catch (e: JSONException) {
                throw RuntimeException(e)
            } catch (e: MalformedURLException) {
                throw RuntimeException(e)
            }
            val command: ParseRESTCommand = ParseRESTObjectBatchCommand(
                "batch", ParseHttpRequest.Method.POST, parameters, sessionToken
            )
            command.executeAsync(client!!)
                .continueWith<Void?>(Continuation { task: Task<JSONObject> ->
                    var tcs: TaskCompletionSource<JSONObject?>
                    if (task.isFaulted || task.isCancelled) {
                        // REST command failed or canceled, fail or cancel all tasks
                        for (i in 0 until batchSize) {
                            tcs = tcss[i]
                            if (task.isFaulted) {
                                tcs.setError(task.error)
                            } else {
                                tcs.setCancelled()
                            }
                        }
                    }
                    val json = task.result
                    val results = json.getJSONArray(KEY_RESULTS)
                    val resultLength = results.length()
                    if (resultLength != batchSize) {
                        // Invalid response, fail all tasks
                        for (i in 0 until batchSize) {
                            tcs = tcss[i]
                            tcs.setError(
                                IllegalStateException(
                                    "Batch command result count expected: $batchSize but was: $resultLength"
                                )
                            )
                        }
                    }
                    for (i in 0 until batchSize) {
                        val result = results.getJSONObject(i)
                        tcs = tcss[i]
                        if (result.has("success")) {
                            val success = result.getJSONObject("success")
                            tcs.setResult(success)
                        } else if (result.has("error")) {
                            val error = result.getJSONObject("error")
                            tcs.setError(
                                ParseException(
                                    error.getInt("code"),
                                    error.getString("error")
                                )
                            )
                        }
                    }
                    null
                })
            return tasks
        }
    }
}
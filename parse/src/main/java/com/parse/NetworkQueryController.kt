/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse

import com.parse.PLog.d
import com.parse.ParseDecoder.Companion.get
import com.parse.ParseQuery.CachePolicy
import com.parse.ParseQuery.RelationConstraint
import com.parse.boltsinternal.Continuation
import com.parse.boltsinternal.Task
import org.json.JSONException
import org.json.JSONObject
import java.util.*

internal class NetworkQueryController(private val restClient: ParseHttpClient) :
    AbstractQueryController() {
    override fun <T : ParseObject?> findAsync(
        state: ParseQuery.State<T>, user: ParseUser?, cancellationToken: Task<Void>?
    ): Task<List<T>> {
        val sessionToken = user?.sessionToken
        return findAsync(state, sessionToken, cancellationToken)
    }

    override fun <T : ParseObject?> countAsync(
        state: ParseQuery.State<T>,
        user: ParseUser?,
        cancellationToken: Task<Void>?
    ): Task<Int> {
        val sessionToken = user?.sessionToken
        return countAsync(state, sessionToken, cancellationToken)
    }

    /**
     * Retrieves a list of [ParseObject]s that satisfy this query from the source.
     *
     * @return A list of all [ParseObject]s obeying the conditions set in this query.
     */
    /* package */
    fun <T : ParseObject?> findAsync(
        state: ParseQuery.State<T>,
        sessionToken: String?,
        ct: Task<Void>?
    ): Task<List<T>> {
        val queryStart = System.nanoTime()
        val command: ParseRESTCommand = ParseRESTQueryCommand.findCommand(state, sessionToken)
        val querySent = System.nanoTime()
        return command.executeAsync(restClient, ct)
            .onSuccess(Continuation { task: Task<JSONObject> ->
                val json = task.result
                // Cache the results, unless we are ignoring the cache
                val policy = state.cachePolicy()
                if (policy != null && policy != CachePolicy.IGNORE_CACHE) {
                    ParseKeyValueCache.saveToKeyValueCache(command.cacheKey, json.toString())
                }
                val queryReceived = System.nanoTime()
                val response: List<T> = convertFindResponse(state, task.result)
                val objectsParsed = System.nanoTime()
                if (json.has("trace")) {
                    val serverTrace = json["trace"]
                    d(
                        "ParseQuery", String.format(
                            """
    Query pre-processing took %f seconds
    %s
    Client side parsing took %f seconds
    
    """.trimIndent(),
                            (querySent - queryStart) / (1000.0f * 1000.0f),
                            serverTrace,
                            (objectsParsed - queryReceived) / (1000.0f * 1000.0f)
                        )
                    )
                }
                response
            }, Task.BACKGROUND_EXECUTOR)
    }

    /* package */
    fun <T : ParseObject?> countAsync(
        state: ParseQuery.State<T>,
        sessionToken: String?,
        ct: Task<Void>?
    ): Task<Int> {
        val command: ParseRESTCommand = ParseRESTQueryCommand.countCommand(state, sessionToken)
        return command.executeAsync(restClient, ct)
            .onSuccessTask(Continuation { task: Task<JSONObject> ->
                // Cache the results, unless we are ignoring the cache
                val policy = state.cachePolicy()
                if (policy != null && policy != CachePolicy.IGNORE_CACHE) {
                    val result = task.result
                    ParseKeyValueCache.saveToKeyValueCache(command.cacheKey, result.toString())
                }
                task
            }, Task.BACKGROUND_EXECUTOR)
            .onSuccess { task: Task<JSONObject> -> task.result.optInt("count") }
    }

    // Converts the JSONArray that represents the results of a find command to an
    // ArrayList<ParseObject>.
    /* package */
    @Throws(JSONException::class)
    fun <T : ParseObject?> convertFindResponse(
        state: ParseQuery.State<T>,
        response: JSONObject
    ): List<T> {
        val answer = ArrayList<T>()
        val results = response.optJSONArray("results")
        if (results == null) {
            d(TAG, "null results in find response")
        } else {
            var resultClassName = response.optString("className", null)
            if (resultClassName == null) {
                resultClassName = state.className()
            }
            for (i in 0 until results.length()) {
                val data = results.getJSONObject(i)
                val `object`: T =
                    ParseObject.fromJSON(data, resultClassName, get(), state.selectedKeys())
                answer.add(`object`)

                /*
                 * If there was a $relatedTo constraint on the query, then add any results to the list of
                 * known objects in the relation for offline caching
                 */
                val relation = state.constraints()["\$relatedTo"] as RelationConstraint?
                relation?.relation?.addKnownObject(`object`)
            }
        }
        return answer
    }

    companion object {
        private const val TAG = "NetworkQueryController"
    }
}
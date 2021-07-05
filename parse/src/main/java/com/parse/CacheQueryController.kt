/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse

import com.parse.ParseQuery.CachePolicy
import com.parse.boltsinternal.Task
import org.json.JSONException

internal class CacheQueryController(private val networkController: NetworkQueryController) :
    AbstractQueryController() {
    override fun <T : ParseObject?> findAsync(
        state: ParseQuery.State<T>,
        user: ParseUser?,
        cancellationToken: Task<Void>?
    ): Task<List<T>> {
        val sessionToken = user?.sessionToken
        val callbacks: CommandDelegate<List<T>> = object : CommandDelegate<List<T>> {
            override fun runOnNetworkAsync(): Task<List<T>> {
                return networkController.findAsync(state, sessionToken, cancellationToken)
            }

            override fun runFromCacheAsync(): Task<List<T>> {
                return findFromCacheAsync(state, sessionToken)
            }
        }
        return runCommandWithPolicyAsync(callbacks, state.cachePolicy())
    }

    override fun <T : ParseObject?> countAsync(
        state: ParseQuery.State<T>,
        user: ParseUser?,
        cancellationToken: Task<Void>?
    ): Task<Int> {
        val sessionToken = user?.sessionToken
        val callbacks: CommandDelegate<Int> = object : CommandDelegate<Int> {
            override fun runOnNetworkAsync(): Task<Int> {
                return networkController.countAsync(state, sessionToken, cancellationToken)
            }

            override fun runFromCacheAsync(): Task<Int> {
                return countFromCacheAsync(state, sessionToken)
            }
        }
        return runCommandWithPolicyAsync(callbacks, state.cachePolicy())
    }

    /**
     * Retrieves the results of the last time [ParseQuery.find] was called on a query
     * identical to this one.
     *
     * @param sessionToken The user requesting access.
     * @return A list of [ParseObject]s corresponding to this query. Returns null if there is no
     * cache for this query.
     */
    private fun <T : ParseObject?> findFromCacheAsync(
        state: ParseQuery.State<T>, sessionToken: String?
    ): Task<List<T>> {
        val cacheKey = ParseRESTQueryCommand.findCommand(state, sessionToken).cacheKey
        return Task.call({
            val cached = ParseKeyValueCache.jsonFromKeyValueCache(cacheKey, state.maxCacheAge())
                ?: throw ParseException(ParseException.CACHE_MISS, "results not cached")
            try {
                return@call networkController.convertFindResponse(state, cached)
            } catch (e: JSONException) {
                throw ParseException(ParseException.CACHE_MISS, "the cache contains corrupted json")
            }
        }, Task.BACKGROUND_EXECUTOR)
    }

    /**
     * Retrieves the results of the last time [ParseQuery.count] was called on a query
     * identical to this one.
     *
     * @param sessionToken The user requesting access.
     * @return A list of [ParseObject]s corresponding to this query. Returns null if there is no
     * cache for this query.
     */
    private fun <T : ParseObject?> countFromCacheAsync(
        state: ParseQuery.State<T>, sessionToken: String?
    ): Task<Int> {
        val cacheKey = ParseRESTQueryCommand.countCommand(state, sessionToken).cacheKey
        return Task.call({
            val cached = ParseKeyValueCache.jsonFromKeyValueCache(cacheKey, state.maxCacheAge())
                ?: throw ParseException(ParseException.CACHE_MISS, "results not cached")
            try {
                return@call cached.getInt("count")
            } catch (e: JSONException) {
                throw ParseException(ParseException.CACHE_MISS, "the cache contains corrupted json")
            }
        }, Task.BACKGROUND_EXECUTOR)
    }

    private fun <TResult> runCommandWithPolicyAsync(
        c: CommandDelegate<TResult>,
        policy: CachePolicy
    ): Task<TResult> {
        return when (policy) {
            CachePolicy.IGNORE_CACHE, CachePolicy.NETWORK_ONLY -> c.runOnNetworkAsync()
            CachePolicy.CACHE_ONLY -> c.runFromCacheAsync()
            CachePolicy.CACHE_ELSE_NETWORK -> c.runFromCacheAsync()
                .continueWithTask { task: Task<TResult> ->
                    if (task.error is ParseException) {
                        return@continueWithTask c.runOnNetworkAsync()
                    }
                    task
                }
            CachePolicy.NETWORK_ELSE_CACHE -> c.runOnNetworkAsync()
                .continueWithTask { task: Task<TResult> ->
                    val error = task.error
                    if (error is ParseException &&
                        error.code == ParseException.CONNECTION_FAILED
                    ) {
                        return@continueWithTask c.runFromCacheAsync()
                    }
                    task
                }
            CachePolicy.CACHE_THEN_NETWORK -> throw RuntimeException(
                "You cannot use the cache policy CACHE_THEN_NETWORK with find()"
            )
        }
    }

    /**
     * A callback that will be used to tell runCommandWithPolicy how to perform the command on the
     * network and form the cache.
     */
    private interface CommandDelegate<T> {
        // Fetches data from the network.
        fun runOnNetworkAsync(): Task<T>

        // Fetches data from the cache.
        fun runFromCacheAsync(): Task<T>
    }
}
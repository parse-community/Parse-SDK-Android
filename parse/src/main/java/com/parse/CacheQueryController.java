/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;
import java.util.concurrent.Callable;

import bolts.Continuation;
import bolts.Task;

class CacheQueryController extends AbstractQueryController {

    private final NetworkQueryController networkController;

    public CacheQueryController(NetworkQueryController network) {
        networkController = network;
    }

    @Override
    public <T extends ParseObject> Task<List<T>> findAsync(
            final ParseQuery.State<T> state,
            final ParseUser user,
            final Task<Void> cancellationToken) {
        final String sessionToken = user != null ? user.getSessionToken() : null;
        CommandDelegate<List<T>> callbacks = new CommandDelegate<List<T>>() {
            @Override
            public Task<List<T>> runOnNetworkAsync() {
                return networkController.findAsync(state, sessionToken, cancellationToken);
            }

            @Override
            public Task<List<T>> runFromCacheAsync() {
                return findFromCacheAsync(state, sessionToken);
            }
        };
        return runCommandWithPolicyAsync(callbacks, state.cachePolicy());
    }

    @Override
    public <T extends ParseObject> Task<Integer> countAsync(
            final ParseQuery.State<T> state,
            final ParseUser user,
            final Task<Void> cancellationToken) {
        final String sessionToken = user != null ? user.getSessionToken() : null;
        CommandDelegate<Integer> callbacks = new CommandDelegate<Integer>() {
            @Override
            public Task<Integer> runOnNetworkAsync() {
                return networkController.countAsync(state, sessionToken, cancellationToken);
            }

            @Override
            public Task<Integer> runFromCacheAsync() {
                return countFromCacheAsync(state, sessionToken);
            }
        };
        return runCommandWithPolicyAsync(callbacks, state.cachePolicy());
    }

    /**
     * Retrieves the results of the last time {@link ParseQuery#find()} was called on a query
     * identical to this one.
     *
     * @param sessionToken The user requesting access.
     * @return A list of {@link ParseObject}s corresponding to this query. Returns null if there is no
     * cache for this query.
     */
    private <T extends ParseObject> Task<List<T>> findFromCacheAsync(
            final ParseQuery.State<T> state, String sessionToken) {
        final String cacheKey = ParseRESTQueryCommand.findCommand(state, sessionToken).getCacheKey();
        return Task.call(new Callable<List<T>>() {
            @Override
            public List<T> call() throws Exception {
                JSONObject cached = ParseKeyValueCache.jsonFromKeyValueCache(cacheKey, state.maxCacheAge());
                if (cached == null) {
                    throw new ParseException(ParseException.CACHE_MISS, "results not cached");
                }
                try {
                    return networkController.convertFindResponse(state, cached);
                } catch (JSONException e) {
                    throw new ParseException(ParseException.CACHE_MISS, "the cache contains corrupted json");
                }
            }
        }, Task.BACKGROUND_EXECUTOR);
    }

    /**
     * Retrieves the results of the last time {@link ParseQuery#count()} was called on a query
     * identical to this one.
     *
     * @param sessionToken The user requesting access.
     * @return A list of {@link ParseObject}s corresponding to this query. Returns null if there is no
     * cache for this query.
     */
    private <T extends ParseObject> Task<Integer> countFromCacheAsync(
            final ParseQuery.State<T> state, String sessionToken) {
        final String cacheKey = ParseRESTQueryCommand.countCommand(state, sessionToken).getCacheKey();
        return Task.call(new Callable<Integer>() {
            @Override
            public Integer call() throws Exception {
                JSONObject cached = ParseKeyValueCache.jsonFromKeyValueCache(cacheKey, state.maxCacheAge());
                if (cached == null) {
                    throw new ParseException(ParseException.CACHE_MISS, "results not cached");
                }
                try {
                    return cached.getInt("count");
                } catch (JSONException e) {
                    throw new ParseException(ParseException.CACHE_MISS, "the cache contains corrupted json");
                }
            }
        }, Task.BACKGROUND_EXECUTOR);
    }

    private <TResult> Task<TResult> runCommandWithPolicyAsync(final CommandDelegate<TResult> c,
                                                              ParseQuery.CachePolicy policy) {
        switch (policy) {
            case IGNORE_CACHE:
            case NETWORK_ONLY:
                return c.runOnNetworkAsync();
            case CACHE_ONLY:
                return c.runFromCacheAsync();
            case CACHE_ELSE_NETWORK:
                return c.runFromCacheAsync().continueWithTask(new Continuation<TResult, Task<TResult>>() {
                    @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
                    @Override
                    public Task<TResult> then(Task<TResult> task) {
                        if (task.getError() instanceof ParseException) {
                            return c.runOnNetworkAsync();
                        }
                        return task;
                    }
                });
            case NETWORK_ELSE_CACHE:
                return c.runOnNetworkAsync().continueWithTask(new Continuation<TResult, Task<TResult>>() {
                    @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
                    @Override
                    public Task<TResult> then(Task<TResult> task) {
                        Exception error = task.getError();
                        if (error instanceof ParseException &&
                                ((ParseException) error).getCode() == ParseException.CONNECTION_FAILED) {
                            return c.runFromCacheAsync();
                        }
                        // Either the query succeeded, or there was an an error with the query, not the
                        // network
                        return task;
                    }
                });
            case CACHE_THEN_NETWORK:
                throw new RuntimeException(
                        "You cannot use the cache policy CACHE_THEN_NETWORK with find()");
            default:
                throw new RuntimeException("Unknown cache policy: " + policy);
        }
    }

    /**
     * A callback that will be used to tell runCommandWithPolicy how to perform the command on the
     * network and form the cache.
     */
    private interface CommandDelegate<T> {
        // Fetches data from the network.
        Task<T> runOnNetworkAsync();

        // Fetches data from the cache.
        Task<T> runFromCacheAsync();
    }
}

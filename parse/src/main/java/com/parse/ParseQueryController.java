/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import java.util.List;

import com.parse.boltsinternal.Task;

/**
 * A {@code ParseQueryController} defines how a {@link ParseQuery} is executed.
 */
interface ParseQueryController {

    /**
     * Executor for {@code find} queries.
     *
     * @param state             Immutable query state to execute.
     * @param user              The user executing the query that can be used to match ACLs.
     * @param cancellationToken Cancellation token.
     * @return A {@link Task} that resolves to the results of the find.
     */
    <T extends ParseObject> Task<List<T>> findAsync(ParseQuery.State<T> state, ParseUser user,
                                                    Task<Void> cancellationToken);

    /**
     * Executor for {@code count} queries.
     *
     * @param state             Immutable query state to execute.
     * @param user              The user executing the query that can be used to match ACLs.
     * @param cancellationToken Cancellation token.
     * @return A {@link Task} that resolves to the results of the count.
     */
    <T extends ParseObject> Task<Integer> countAsync(ParseQuery.State<T> state, ParseUser user,
                                                     Task<Void> cancellationToken);

    /**
     * Executor for {@code getFirst} queries.
     *
     * @param state             Immutable query state to execute.
     * @param user              The user executing the query that can be used to match ACLs.
     * @param cancellationToken Cancellation token.
     * @return A {@link Task} that resolves to the the first result of the query if successful and
     * there is at least one result or {@link ParseException#OBJECT_NOT_FOUND} if there are no
     * results.
     */
    <T extends ParseObject> Task<T> getFirstAsync(ParseQuery.State<T> state, ParseUser user,
                                                  Task<Void> cancellationToken);
}

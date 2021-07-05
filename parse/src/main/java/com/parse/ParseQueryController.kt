/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse

import com.parse.boltsinternal.Task

/**
 * A `ParseQueryController` defines how a [ParseQuery] is executed.
 */
internal interface ParseQueryController {
    /**
     * Executor for `find` queries.
     *
     * @param state             Immutable query state to execute.
     * @param user              The user executing the query that can be used to match ACLs.
     * @param cancellationToken Cancellation token.
     * @return A [Task] that resolves to the results of the find.
     */
    fun <T : ParseObject> findAsync(
        state: ParseQuery.State<T>, user: ParseUser?,
        cancellationToken: Task<Void>?
    ): Task<List<T>>

    /**
     * Executor for `count` queries.
     *
     * @param state             Immutable query state to execute.
     * @param user              The user executing the query that can be used to match ACLs.
     * @param cancellationToken Cancellation token.
     * @return A [Task] that resolves to the results of the count.
     */
    fun <T : ParseObject> countAsync(
        state: ParseQuery.State<T>, user: ParseUser?,
        cancellationToken: Task<Void>?
    ): Task<Int?>

    /**
     * Executor for `getFirst` queries.
     *
     * @param state             Immutable query state to execute.
     * @param user              The user executing the query that can be used to match ACLs.
     * @param cancellationToken Cancellation token.
     * @return A [Task] that resolves to the the first result of the query if successful and
     * there is at least one result or [ParseException.OBJECT_NOT_FOUND] if there are no
     * results.
     */
    fun <T : ParseObject> getFirstAsync(
        state: ParseQuery.State<T>, user: ParseUser?,
        cancellationToken: Task<Void>?
    ): Task<T>
}
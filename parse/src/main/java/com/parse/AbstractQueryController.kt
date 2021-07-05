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
 * `AbstractParseQueryController` is an abstract implementation of
 * [ParseQueryController], which implements [ParseQueryController.getFirstAsync].
 */
internal abstract class AbstractQueryController : ParseQueryController {
    override fun <T : ParseObject> getFirstAsync(
        state: ParseQuery.State<T>, user: ParseUser?,
        cancellationToken: Task<Void>?
    ): Task<T> {
        return findAsync(state, user, cancellationToken).continueWith { task: Task<List<T>?> ->
            if (task.isFaulted) {
                throw task.error
            }

            task.result?.let {
                if (it.isNotEmpty()) {
                    return@continueWith it[0]
                }
            }

            throw ParseException(ParseException.OBJECT_NOT_FOUND, "no results found for query")
        }
    }
}
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

internal class OfflineQueryController(
    private val offlineStore: OfflineStore,
    private val networkController: ParseQueryController
) : AbstractQueryController() {
    override fun <T : ParseObject> findAsync(
        state: ParseQuery.State<T>,
        user: ParseUser?,
        cancellationToken: Task<Void>?
    ): Task<List<T>> {
        return if (state.isFromLocalDatastore) {
            offlineStore.findFromPinAsync(state.pinName(), state, user)!!
        } else {
            networkController.findAsync(state, user, cancellationToken)
        }
    }

    override fun <T : ParseObject> countAsync(
        state: ParseQuery.State<T>,
        user: ParseUser?,
        cancellationToken: Task<Void>?
    ): Task<Int?> {
        return if (state.isFromLocalDatastore) {
            offlineStore.countFromPinAsync(state.pinName(), state, user)!!
        } else {
            networkController.countAsync(state, user, cancellationToken)
        }
    }
}
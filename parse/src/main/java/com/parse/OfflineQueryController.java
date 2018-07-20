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

import bolts.Task;

class OfflineQueryController extends AbstractQueryController {

    private final OfflineStore offlineStore;
    private final ParseQueryController networkController;

    public OfflineQueryController(OfflineStore store, ParseQueryController network) {
        offlineStore = store;
        networkController = network;
    }

    @Override
    public <T extends ParseObject> Task<List<T>> findAsync(
            ParseQuery.State<T> state,
            ParseUser user,
            Task<Void> cancellationToken) {
        if (state.isFromLocalDatastore()) {
            return offlineStore.findFromPinAsync(state.pinName(), state, user);
        } else {
            return networkController.findAsync(state, user, cancellationToken);
        }
    }

    @Override
    public <T extends ParseObject> Task<Integer> countAsync(
            ParseQuery.State<T> state,
            ParseUser user,
            Task<Void> cancellationToken) {
        if (state.isFromLocalDatastore()) {
            return offlineStore.countFromPinAsync(state.pinName(), state, user);
        } else {
            return networkController.countAsync(state, user, cancellationToken);
        }
    }
}

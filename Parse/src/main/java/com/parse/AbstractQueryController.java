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

import bolts.Continuation;
import bolts.Task;

/**
 * {@code AbstractParseQueryController} is an abstract implementation of
 * {@link ParseQueryController}, which implements {@link ParseQueryController#getFirstAsync}.
 */
/** package */ abstract class AbstractQueryController implements ParseQueryController {

  @Override
  public <T extends ParseObject> Task<T> getFirstAsync(ParseQuery.State<T> state, ParseUser user,
      Task<Void> cancellationToken) {
    return findAsync(state, user, cancellationToken).continueWith(new Continuation<List<T>, T>() {
      @Override
      public T then(Task<List<T>> task) throws Exception {
        if (task.isFaulted()) {
          throw task.getError();
        }
        if (task.getResult() != null && task.getResult().size() > 0) {
          return task.getResult().get(0);
        }
        throw new ParseException(ParseException.OBJECT_NOT_FOUND, "no results found for query");
      }
    });
  }
}

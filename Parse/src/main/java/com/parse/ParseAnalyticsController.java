/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import org.json.JSONObject;

import java.util.Map;

import bolts.Task;

/** package */ class ParseAnalyticsController {

  /* package for test */ ParseEventuallyQueue eventuallyQueue;

  public ParseAnalyticsController(ParseEventuallyQueue eventuallyQueue) {
    this.eventuallyQueue = eventuallyQueue;
  }

  public Task<Void> trackEventInBackground(final String name,
    Map<String, String> dimensions, String sessionToken) {
    ParseRESTCommand command = ParseRESTAnalyticsCommand.trackEventCommand(name, dimensions,
        sessionToken);

    Task<JSONObject> eventuallyTask = eventuallyQueue.enqueueEventuallyAsync(command, null);
    return eventuallyTask.makeVoid();
  }

  public Task<Void> trackAppOpenedInBackground(String pushId, String sessionToken) {
    ParseRESTCommand command = ParseRESTAnalyticsCommand.trackAppOpenedCommand(pushId,
        sessionToken);

    Task<JSONObject> eventuallyTask = eventuallyQueue.enqueueEventuallyAsync(command, null);
    return eventuallyTask.makeVoid();
  }
}

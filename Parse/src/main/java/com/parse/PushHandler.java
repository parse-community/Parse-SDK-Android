/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import android.content.Intent;
import android.os.IBinder;
import android.support.annotation.WorkerThread;

import bolts.Task;

/**
 * An interface for for handling push payloads (or any similar events, e.g. registration)
 * that woke up the {@link com.parse.PushService}.
 * Each subclass represent a certain {@link com.parse.PushType}.
 */
/** package */ interface PushHandler {

  Task<Void> initialize();

  @WorkerThread
  void handlePush(Intent intent);
}

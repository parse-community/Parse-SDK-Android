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

/**
 * An interface for incomplete {@link Serivce} that follow the same structure as a real
 * {@code Service}, but aren't real {@code Service}s.]
 * <p />
 * This mainly exists since we utilize {@link PushService} as a router for multiple
 * {@code ProxyService} implementations.
 */
/** package */ interface ProxyService {
  void onCreate();
  void onDestroy();

  int onStartCommand(Intent intent, int flags, int startId);

  IBinder onBind(Intent intent);
}

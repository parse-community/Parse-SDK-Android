/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

import bolts.Continuation;
import bolts.Task;

/** package */ class ParseAuthenticationManager {

  private final Object lock = new Object();
  private final Map<String, ParseAuthenticationCallbacks> callbacks = new HashMap<>();
  private final ParseCurrentUserController controller;

  public ParseAuthenticationManager(ParseCurrentUserController controller) {
    this.controller = controller;
  }

  public void register(final String authType, ParseAuthenticationCallbacks callbacks) {
    if (authType == null) {
      throw new IllegalArgumentException("Invalid authType: " + null);
    }

    synchronized (lock) {
      if (this.callbacks.containsKey(authType)) {
        throw new IllegalStateException("Callbacks already registered for <" + authType + ">: "
            + this.callbacks.get(authType));
      }
      this.callbacks.put(authType, callbacks);
    }

    if (ParseAnonymousUtils.AUTH_TYPE.equals(authType)) {
      // There's nothing to synchronize
      return;
    }

    // Synchronize the current user with the auth callbacks.
    controller.getAsync(false).onSuccessTask(new Continuation<ParseUser, Task<Void>>() {
      @Override
      public Task<Void> then(Task<ParseUser> task) throws Exception {
        ParseUser user = task.getResult();
        if (user != null) {
          return user.synchronizeAuthDataAsync(authType);
        }
        return null;
      }
    });
  }

  public Task<Boolean> restoreAuthenticationAsync(String authType, final Map<String, String> authData) {
    final ParseAuthenticationCallbacks callbacks;
    synchronized (lock) {
      callbacks = this.callbacks.get(authType);
    }
    if (callbacks == null) {
      return Task.forResult(true);
    }
    return Task.call(new Callable<Boolean>() {
      @Override
      public Boolean call() throws Exception {
        return callbacks.onRestoreAuthentication(authData);
      }
    }, ParseExecutors.io());
  }

  public Task<Void> deauthenticateAsync(String authType) {
    final ParseAuthenticationCallbacks callbacks;
    synchronized (lock) {
      callbacks = this.callbacks.get(authType);
    }
    if (callbacks != null) {
      return Task.call(new Callable<Void>() {
        @Override
        public Void call() throws Exception {
          callbacks.onDeauthenticate();
          return null;
        }
      }, ParseExecutors.io());
    }
    return Task.forResult(null);
  }
}

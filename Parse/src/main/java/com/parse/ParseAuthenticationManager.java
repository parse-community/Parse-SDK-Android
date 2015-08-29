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

import bolts.Continuation;
import bolts.Task;

/** package */ class ParseAuthenticationManager {

  private final Object lock = new Object();
  private final Map<String, ParseAuthenticationProvider> authenticationProviders = new HashMap<>();
  private final ParseCurrentUserController controller;

  public ParseAuthenticationManager(ParseCurrentUserController controller) {
    this.controller = controller;
  }

  public void register(ParseAuthenticationProvider provider) {
    final String authType = provider.getAuthType();
    if (authType == null) {
      throw new IllegalArgumentException("Invalid authType: " + null);
    }

    synchronized (lock) {
      if (authenticationProviders.containsKey(authType)) {
        throw new IllegalStateException("Another " + authType + " provider was already registered: "
            + authenticationProviders.get(authType));
      }
      authenticationProviders.put(provider.getAuthType(), provider);
    }

    if (provider instanceof AnonymousAuthenticationProvider) {
      // There's nothing to synchronize
      return;
    }

    // Synchronize the current user with the auth provider.
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

  public Task<Boolean> restoreAuthenticationAsync(String authType, Map<String, String> authData) {
    ParseAuthenticationProvider provider;
    synchronized (lock) {
      provider = authenticationProviders.get(authType);
    }
    if (provider == null) {
      return Task.forResult(true);
    }
    return provider.restoreAuthenticationAsync(authData);
  }

  public Task<Void> deauthenticateAsync(String authType) {
    ParseAuthenticationProvider provider;
    synchronized (lock) {
      provider = authenticationProviders.get(authType);
    }
    if (provider != null) {
      return provider.deauthenticateAsync();
    }
    return Task.forResult(null);
  }
}

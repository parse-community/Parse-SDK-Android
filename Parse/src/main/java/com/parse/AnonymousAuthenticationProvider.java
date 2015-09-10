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
import java.util.UUID;

import bolts.Task;

/**
 * An authentication provider that generates a random UUID that will be used as
 * a key to identify this anonymous user until the user has been claimed.
 */
/** package */ class AnonymousAuthenticationProvider implements ParseAuthenticationProvider {

  @Override
  public Task<Map<String, String>> authenticateAsync() {
    return Task.forResult(getAuthData());
  }

  public Map<String, String> getAuthData() {
    Map<String, String> authData = new HashMap<>();
    authData.put("id", UUID.randomUUID().toString());
    return authData;
  }

  @Override
  public Task<Void> deauthenticateAsync() {
    // do nothing
    return Task.forResult(null);
  }

  @Override
  public Task<Boolean> restoreAuthenticationAsync(Map<String, String> authData) {
    return Task.forResult(true);
  }

  @Override
  public String getAuthType() {
    return ParseAnonymousUtils.AUTH_TYPE;
  }
}

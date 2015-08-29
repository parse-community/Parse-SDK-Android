/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import java.util.Map;

import bolts.Task;

/**
 * Provides a general interface for delegation of the authentication process.
 */
/** package */ interface ParseAuthenticationProvider {

  /**
   * Provides a unique name for the type of authentication the provider does.
   * For example, the FacebookAuthenticationProvider would return "facebook".
   */
  String getAuthType();

  /**
   * Authenticates with the service.
   *
   * @return A {@code Task} that will be resolved when authentication is complete.
   */
  Task<Map<String, String>> authenticateAsync();

  /**
   * Deauthenticates (logs out) the user associated with this provider. This call may block.
   *
   * @return A {@link Task} that resolves when deauthentication is complete.
   */
  Task<Void> deauthenticateAsync();

  /**
   * Restores authentication that has been serialized, such as session keys,
   * etc.
   * 
   * @param authData
   *          the auth data for the provider. This value may be null when
   *          unlinking an account.
   *
   * @return A {@link Task} that resolves to {@code true} iff the {@code authData} was successfully
   *         synchronized or {@code false} if user should no longer be associated because of bad
   *         {@code authData}.
   */
  Task<Boolean> restoreAuthenticationAsync(Map<String, String> authData);
}

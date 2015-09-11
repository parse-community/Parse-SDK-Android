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
 * Provides a general interface for delegation of third party authentication.
 */
public interface ParseAuthenticationProvider {

  /**
   * @return A unique name for the type of authentication the provider does.
   * <p />
   * For example, the {@code FacebookAuthenticationProvider} would return {@code "facebook"}.
   */
  String getAuthType();

  /**
   * Deauthenticates (logs out) the user associated with this provider.
   *
   * @return A {@link Task} that resolves when deauthentication completes.
   */
  Task<Void> deauthenticateInBackground();

  /**
   * Restores authentication that has been serialized, such as session keys, etc.
   * 
   * @param authData
   *          The auth data for the provider. This value may be {@code null} when
   *          unlinking an account.
   *
   * @return A {@link Task} that resolves to {@code true} iff the {@code authData} was successfully
   *         synchronized or {@code false} if user should no longer be associated because of bad
   *         {@code authData}.
   */
  Task<Void> restoreAuthenticationInBackground(Map<String, String> authData);
}

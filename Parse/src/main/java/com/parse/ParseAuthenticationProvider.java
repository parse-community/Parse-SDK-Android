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
   * Begins the authentication process and invokes onSuccess() or onError() on
   * the callback upon completion. This call should not block.
   *
   * @return A task that will be resolved upon the completion of authentication.
   */
  Task<Map<String, String>> authenticateAsync();

  /**
   * Deauthenticates (logs out) the user associated with this provider. This
   * call may block.
   */
  Task<Void> deauthenticateAsync();

  /**
   * Restores authentication that has been serialized, such as session keys,
   * etc.
   * 
   * @param authData
   *          the auth data for the provider. This value may be null when
   *          unlinking an account.
   * @return true iff the authData was successfully synchronized. A false return
   *         value indicates that the user should no longer be associated
   *         because of bad auth data.
   */
  boolean restoreAuthentication(Map<String, String> authData);
}

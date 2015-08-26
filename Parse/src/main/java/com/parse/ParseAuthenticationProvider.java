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

import bolts.Continuation;
import bolts.Task;

/**
 * Provides a general interface for delegation of the authentication process.
 */
/** package */ abstract class ParseAuthenticationProvider {

  /**
   * Provides a unique name for the type of authentication the provider does.
   * For example, the FacebookAuthenticationProvider would return "facebook".
   */
  public abstract String getAuthType();

  /**
   * Begins the authentication process and invokes onSuccess() or onError() on
   * the callback upon completion. This call should not block.
   *
   * @return A task that will be resolved upon the completion of authentication.
   */
  public abstract Task<Map<String, String>> authenticateAsync();

  /**
   * Deauthenticates (logs out) the user associated with this provider. This
   * call may block.
   */
  public abstract void deauthenticate();

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
  public abstract boolean restoreAuthentication(Map<String, String> authData);

  public abstract void cancel();

  public Task<ParseUser> logInAsync() {
    return authenticateAsync().onSuccessTask(new Continuation<Map<String, String>, Task<ParseUser>>() {
      @Override
      public Task<ParseUser> then(Task<Map<String, String>> task) throws Exception {
        return logInAsync(task.getResult());
      }
    });
  }

  public Task<ParseUser> logInAsync(Map<String, String> authData) {
    return ParseUser.logInWithAsync(getAuthType(), authData);
  }

  public Task<Void> linkAsync(final ParseUser user) {
    return authenticateAsync().onSuccessTask(new Continuation<Map<String, String>, Task<Void>>() {
      @Override
      public Task<Void> then(Task<Map<String, String>> task) throws Exception {
        return linkAsync(user, task.getResult());
      }
    });
  }

  public Task<Void> linkAsync(ParseUser user, Map<String, String> authData) {
    return user.linkWithAsync(getAuthType(), authData, user.getSessionToken());
  }

  public Task<Void> unlinkAsync(ParseUser user) {
    return user.unlinkFromAsync(getAuthType());
  }
}

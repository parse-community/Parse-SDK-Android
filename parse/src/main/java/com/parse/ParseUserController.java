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

import com.parse.boltsinternal.Task;

interface ParseUserController {

    Task<ParseUser.State> signUpAsync(
            ParseObject.State state,
            ParseOperationSet operations,
            String sessionToken);

    //region logInAsync

    Task<ParseUser.State> logInAsync(
            String username, String password);

    Task<ParseUser.State> logInAsync(
            ParseUser.State state, ParseOperationSet operations);

    Task<ParseUser.State> logInAsync(
            String authType, Map<String, String> authData);

    //endregion

    Task<ParseUser.State> getUserAsync(String sessionToken);

    Task<Void> requestPasswordResetAsync(String email);
}

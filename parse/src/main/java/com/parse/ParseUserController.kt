/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse

import com.parse.boltsinternal.Task

internal interface ParseUserController {
    fun signUpAsync(
        state: ParseObject.State,
        operations: ParseOperationSet,
        sessionToken: String
    ): Task<ParseUser.State>

    //region logInAsync

    fun logInAsync(
        username: String, password: String
    ): Task<ParseUser.State>

    fun logInAsync(
        state: ParseUser.State, operations: ParseOperationSet
    ): Task<ParseUser.State>

    fun logInAsync(
        authType: String, authData: Map<String, String> = emptyMap()
    ): Task<ParseUser.State>

    //endregion
    fun getUserAsync(sessionToken: String): Task<ParseUser.State>
    fun requestPasswordResetAsync(email: String): Task<Void>
}
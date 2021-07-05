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

internal interface ParseSessionController {
    fun getSessionAsync(sessionToken: String): Task<ParseObject.State>
    fun revokeAsync(sessionToken: String): Task<Void>
    fun upgradeToRevocable(sessionToken: String): Task<ParseObject.State>
}
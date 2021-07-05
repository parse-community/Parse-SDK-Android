/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse

/**
 * Provides a general interface for delegation of third party authentication callbacks.
 */
interface AuthenticationCallback {
    /**
     * Called when restoring third party authentication credentials that have been serialized,
     * such as session keys, etc.
     *
     *
     * **Note:** This will be executed on a background thread.
     *
     * @param authData The auth data for the provider. This value may be `null` when
     * unlinking an account.
     * @return `true` if the `authData` was successfully synchronized or `false`
     * if user should no longer be associated because of bad `authData`.
     */
    fun onRestore(authData: Map<String, String> = emptyMap()): Boolean
}
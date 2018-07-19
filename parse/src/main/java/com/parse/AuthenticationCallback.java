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

/**
 * Provides a general interface for delegation of third party authentication callbacks.
 */
public interface AuthenticationCallback {
    /**
     * Called when restoring third party authentication credentials that have been serialized,
     * such as session keys, etc.
     * <p/>
     * <strong>Note:</strong> This will be executed on a background thread.
     *
     * @param authData The auth data for the provider. This value may be {@code null} when
     *                 unlinking an account.
     * @return {@code true} iff the {@code authData} was successfully synchronized or {@code false}
     * if user should no longer be associated because of bad {@code authData}.
     */
    boolean onRestore(Map<String, String> authData);
}

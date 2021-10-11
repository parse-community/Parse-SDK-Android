/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse.facebook;

import com.facebook.AccessToken;
import com.facebook.AccessTokenSource;

/** package */
class TestUtils {
    static AccessToken newAccessToken() {
        return new AccessToken(
                "test_token",
                "test_application_id",
                "test_id",
                null,
                null,
                null,
                AccessTokenSource.DEVICE_AUTH,
                null,
                null,
                null);
    }
}

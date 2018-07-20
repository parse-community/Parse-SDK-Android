/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import bolts.Task;

interface ParseSessionController {

    Task<ParseObject.State> getSessionAsync(String sessionToken);

    Task<Void> revokeAsync(String sessionToken);

    Task<ParseObject.State> upgradeToRevocable(String sessionToken);
}

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
 * A `ProgressCallback` is used to get upload or download progress of a [ParseFile]
 * action.
 *
 *
 * The easiest way to use a `ProgressCallback` is through an anonymous inner class.
 */
// FYI, this does not extend ParseCallback2 since it does not match the usual signature
// done(T, ParseException), but is done(T).
interface ProgressCallback {
    /**
     * Override this function with your desired callback.
     */
    fun done(percentDone: Int)
}
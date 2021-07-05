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
 * A `ParseCallback` is used to do something after a background task completes. End users will
 * use a specific subclass of `ParseCallback`.
 */
interface ParseCallback2<T1, T2 : Throwable?> {
    /**
     * `done(t1, t2)` must be overridden when you are doing a background operation. It is called
     * when the background operation completes.
     *
     *
     * If the operation is successful, `t1` will contain the results and `t2` will be
     * `null`.
     *
     *
     * If the operation was unsuccessful, `t1` will be `null` and `t2` will contain
     * information about the operation failure.
     *
     * @param t1 Generally the results of the operation.
     * @param t2 Generally an [Throwable] that was thrown by the operation, if there was any.
     */
    fun done(t1: T1?, t2: T2?)
}
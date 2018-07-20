/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

/**
 * A {@code GetDataCallback} is used to run code after a {@link ParseFile} fetches its data on a
 * background thread.
 * <p/>
 * The easiest way to use a {@code GetDataCallback} is through an anonymous inner class. Override
 * the {@code done} function to specify what the callback should do after the fetch is complete.
 * The {@code done} function will be run in the UI thread, while the fetch happens in a
 * background thread. This ensures that the UI does not freeze while the fetch happens.
 * <p/>
 * <pre>
 * file.getDataInBackground(new GetDataCallback() {
 *   public void done(byte[] data, ParseException e) {
 *     // ...
 *   }
 * });
 * </pre>
 */
public interface GetDataCallback extends ParseCallback2<byte[], ParseException> {
    /**
     * Override this function with the code you want to run after the fetch is complete.
     *
     * @param data The data that was retrieved, or {@code null} if it did not succeed.
     * @param e    The exception raised by the fetch, or {@code null} if it succeeded.
     */
    @Override
    void done(byte[] data, ParseException e);
}


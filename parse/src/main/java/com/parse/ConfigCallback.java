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
 * A {@code ConfigCallback} is used to run code after {@link ParseConfig#getInBackground()} is used
 * to fetch a new configuration object from the server in a background thread.
 * <p>
 * The easiest way to use a {@code ConfigCallback} is through an anonymous inner class. Override the
 * {@code done} function to specify what the callback should do after the fetch is complete.
 * The {@code done} function will be run in the UI thread, while the fetch happens in a
 * background thread. This ensures that the UI does not freeze while the fetch happens.
 * <p/>
 * <pre>
 * ParseConfig.getInBackground(new ConfigCallback() {
 *   public void done(ParseConfig config, ParseException e) {
 *     if (e == null) {
 *       configFetchSuccess(object);
 *     } else {
 *       configFetchFailed(e);
 *     }
 *   }
 * });
 * </pre>
 */
public interface ConfigCallback extends ParseCallback2<ParseConfig, ParseException> {
    /**
     * Override this function with the code you want to run after the fetch is complete.
     *
     * @param config A new {@code ParseConfig} instance from the server, or {@code null} if it did not
     *               succeed.
     * @param e      The exception raised by the fetch, or {@code null} if it succeeded.
     */
    @Override
    void done(ParseConfig config, ParseException e);
}

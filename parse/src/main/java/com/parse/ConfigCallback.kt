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
 * A `ConfigCallback` is used to run code after [ParseConfig.getInBackground] is used
 * to fetch a new configuration object from the server in a background thread.
 *
 *
 * The easiest way to use a `ConfigCallback` is through an anonymous inner class. Override the
 * `done` function to specify what the callback should do after the fetch is complete.
 * The `done` function will be run in the UI thread, while the fetch happens in a
 * background thread. This ensures that the UI does not freeze while the fetch happens.
 *
 *
 * <pre>
 * ParseConfig.getInBackground(new ConfigCallback() {
 * public void done(ParseConfig config, ParseException e) {
 * if (e == null) {
 * configFetchSuccess(object);
 * } else {
 * configFetchFailed(e);
 * }
 * }
 * });
</pre> *
 */
internal interface ConfigCallback : ParseCallback2<ParseConfig?, ParseException?> {
    /**
     * Override this function with the code you want to run after the fetch is complete.
     *
     * @param config A new `ParseConfig` instance from the server, or `null` if it did not
     * succeed.
     * @param e      The exception raised by the fetch, or `null` if it succeeded.
     */
    override fun done(config: ParseConfig?, e: ParseException?)
}
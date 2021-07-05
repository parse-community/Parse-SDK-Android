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
 * A `SendCallback` is used to run code after sending a [ParsePush] in a background
 * thread.
 *
 *
 * The easiest way to use a `SendCallback` is through an anonymous inner class. Override the
 * `done` function to specify what the callback should do after the send is complete. The
 * `done` function will be run in the UI thread, while the send happens in a background
 * thread. This ensures that the UI does not freeze while the send happens.
 *
 *
 * For example, this sample code sends the message `"Hello world"` on the
 * `"hello"` channel and logs whether the send succeeded.
 *
 *
 * <pre>
 * ParsePush push = new ParsePush();
 * push.setChannel(&quot;hello&quot;);
 * push.setMessage(&quot;Hello world!&quot;);
 * push.sendInBackground(new SendCallback() {
 * public void done(ParseException e) {
 * if (e == null) {
 * Log.d(&quot;push&quot;, &quot;success!&quot;);
 * } else {
 * Log.d(&quot;push&quot;, &quot;failure&quot;);
 * }
 * }
 * });
</pre> *
 */
internal interface SendCallback : ParseCallback1<ParseException?> {
    /**
     * Override this function with the code you want to run after the send is complete.
     *
     * @param e The exception raised by the send, or `null` if it succeeded.
     */
    override fun done(e: ParseException?)
}
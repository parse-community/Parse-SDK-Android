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
 * A {@code SendCallback} is used to run code after sending a {@link ParsePush} in a background
 * thread.
 * <p/>
 * The easiest way to use a {@code SendCallback} is through an anonymous inner class. Override the
 * {@code done} function to specify what the callback should do after the send is complete. The
 * {@code done} function will be run in the UI thread, while the send happens in a background
 * thread. This ensures that the UI does not freeze while the send happens.
 * <p/>
 * For example, this sample code sends the message {@code "Hello world"} on the
 * {@code "hello"} channel and logs whether the send succeeded.
 * <p/>
 * <pre>
 * ParsePush push = new ParsePush();
 * push.setChannel(&quot;hello&quot;);
 * push.setMessage(&quot;Hello world!&quot;);
 * push.sendInBackground(new SendCallback() {
 *   public void done(ParseException e) {
 *     if (e == null) {
 *       Log.d(&quot;push&quot;, &quot;success!&quot;);
 *     } else {
 *       Log.d(&quot;push&quot;, &quot;failure&quot;);
 *     }
 *   }
 * });
 * </pre>
 */
public interface SendCallback extends ParseCallback1<ParseException> {
    /**
     * Override this function with the code you want to run after the send is complete.
     *
     * @param e The exception raised by the send, or {@code null} if it succeeded.
     */
    @Override
    void done(ParseException e);
}

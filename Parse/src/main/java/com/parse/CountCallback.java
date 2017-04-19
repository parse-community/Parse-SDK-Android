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
 * A {@code CountCallback} is used to run code after a {@link ParseQuery} is used to count objects
 * matching a query in a background thread.
 * <p/>
 * The easiest way to use a {@code CountCallback} is through an anonymous inner class. Override the
 * {@code done} function to specify what the callback should do after the count is complete.
 * The {@code done} function will be run in the UI thread, while the count happens in a
 * background thread. This ensures that the UI does not freeze while the fetch happens.
 * <p/>
 * For example, this sample code counts objects of class {@code "MyClass"}. It calls a
 * different function depending on whether the count succeeded or not.
 * <p/>
 * <pre>
 * ParseQuery&lt;ParseObject&gt; query = ParseQuery.getQuery(&quot;MyClass&quot;);
 * query.countInBackground(new CountCallback() {
 *   public void done(int count, ParseException e) {
 *     if (e == null) {
 *       objectsWereCountedSuccessfully(count);
 *     } else {
 *       objectCountingFailed();
 *     }
 *   }
 * });
 * </pre>
 */
// FYI, this does not extend ParseCallback2 since the first param is `int`, which can't be used
// in a generic.
public interface CountCallback {
  /**
   * Override this function with the code you want to run after the count is complete.
   * 
   * @param count
   *          The number of objects matching the query, or -1 if it failed.
   * @param e
   *          The exception raised by the count, or null if it succeeded.
   */
  void done(int count, ParseException e);
}

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
 * A {@code GetCallback} is used to run code after a {@link ParseQuery} is used to fetch a
 * {@link ParseObject} in a background thread.
 * <p/>
 * The easiest way to use a {@code GetCallback} is through an anonymous inner class. Override the
 * {@code done} function to specify what the callback should do after the fetch is complete.
 * The {@code done} function will be run in the UI thread, while the fetch happens in a
 * background thread. This ensures that the UI does not freeze while the fetch happens.
 * <p/>
 * For example, this sample code fetches an object of class {@code "MyClass"} and id
 * {@code myId}. It calls a different function depending on whether the fetch succeeded or not.
 * <p/>
 * <pre>
 * ParseQuery&lt;ParseObject&gt; query = ParseQuery.getQuery(&quot;MyClass&quot;);
 * query.getInBackground(myId, new GetCallback&lt;ParseObject&gt;() {
 *   public void done(ParseObject object, ParseException e) {
 *     if (e == null) {
 *       objectWasRetrievedSuccessfully(object);
 *     } else {
 *       objectRetrievalFailed();
 *     }
 *   }
 * });
 * </pre>
 */
public interface GetCallback<T extends ParseObject> extends ParseCallback2<T, ParseException> {
  /**
   * Override this function with the code you want to run after the fetch is complete.
   * 
   * @param object
   *          The object that was retrieved, or {@code null} if it did not succeed.
   * @param e
   *          The exception raised by the fetch, or {@code null} if it succeeded.
   */
  @Override
  void done(T object, ParseException e);
}

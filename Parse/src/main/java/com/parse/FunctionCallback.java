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
 * A {@code FunctionCallback} is used to run code after {@link ParseCloud#callFunction} is used to
 * run a Cloud Function in a background thread.
 * <p/>
 * The easiest way to use a {@code FunctionCallback} is through an anonymous inner class. Override
 * the {@code done} function to specify what the callback should do after the cloud function is
 * complete. The {@code done} function will be run in the UI thread, while the fetch happens in
 * a background thread. This ensures that the UI does not freeze while the fetch happens.
 * <p/>
 * For example, this sample code calls a cloud function {@code "MyFunction"} with
 * {@code params} and calls a different function depending on whether the function succeeded.
 * <p/>
 * <pre>
 * ParseCloud.callFunctionInBackground(&quot;MyFunction&quot;new, params, FunctionCallback<ParseObject>() {
 *   public void done(ParseObject object, ParseException e) {
 *     if (e == null) {
 *       cloudFunctionSucceeded(object);
 *     } else {
 *       cloudFunctionFailed();
 *     }
 *   }
 * });
 * </pre>
 * 
 * @param <T>
 *          The type of object returned by the Cloud Function.
 */
public interface FunctionCallback<T> extends ParseCallback2<T, ParseException> {
  /**
   * Override this function with the code you want to run after the cloud function is complete.
   * 
   * @param object
   *          The object that was returned by the cloud function.
   * @param e
   *          The exception raised by the cloud call, or {@code null} if it succeeded.
   */
  @Override
  void done(T object, ParseException e);
}

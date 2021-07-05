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
 * A `FindCallback` is used to run code after a [ParseQuery] is used to fetch a list of
 * [ParseObject]s in a background thread.
 *
 *
 * The easiest way to use a `FindCallback` is through an anonymous inner class. Override the
 * `done` function to specify what the callback should do after the fetch is complete.
 * The `done` function will be run in the UI thread, while the fetch happens in a
 * background thread. This ensures that the UI does not freeze while the fetch happens.
 *
 *
 * For example, this sample code fetches all objects of class `"MyClass"`. It calls a
 * different function depending on whether the fetch succeeded or not.
 *
 *
 * <pre>
 * ParseQuery&lt;ParseObject&gt; query = ParseQuery.getQuery(&quot;MyClass&quot;);
 * query.findInBackground(new FindCallback&lt;ParseObject&gt;() {
 * public void done(List&lt;ParseObject&gt; objects, ParseException e) {
 * if (e == null) {
 * objectsWereRetrievedSuccessfully(objects);
 * } else {
 * objectRetrievalFailed();
 * }
 * }
 * });
</pre> *
 */
interface FindCallback<T : ParseObject?> : ParseCallback2<List<T>?, ParseException?> {
    /**
     * Override this function with the code you want to run after the fetch is complete.
     *
     * @param objects The objects that were retrieved, or null if it did not succeed.
     * @param e       The exception raised by the save, or null if it succeeded.
     */
    override fun done(objects: List<T>?, e: ParseException?)
}
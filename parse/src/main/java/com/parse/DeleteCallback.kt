/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse

import com.parse.ParseCallback1

/**
 * A `DeleteCallback` is used to run code after saving a [ParseObject] in a background
 * thread.
 *
 *
 * The easiest way to use a `DeleteCallback` is through an anonymous inner class. Override the
 * `done` function to specify what the callback should do after the delete is complete.
 * The `done` function will be run in the UI thread, while the delete happens in a
 * background thread. This ensures that the UI does not freeze while the delete happens.
 *
 *
 * For example, this sample code deletes the object `myObject` and calls a different
 * function depending on whether the save succeeded or not.
 *
 *
 * <pre>
 * myObject.deleteInBackground(new DeleteCallback() {
 * public void done(ParseException e) {
 * if (e == null) {
 * myObjectWasDeletedSuccessfully();
 * } else {
 * myObjectDeleteDidNotSucceed();
 * }
 * }
 * });
</pre> *
 */
interface DeleteCallback : ParseCallback1<ParseException?> {
    /**
     * Override this function with the code you want to run after the delete is complete.
     *
     * @param e The exception raised by the delete, or `null` if it succeeded.
     */
    override fun done(e: ParseException?)
}
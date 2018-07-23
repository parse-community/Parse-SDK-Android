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
 * A {@code LocationCallback} is used to run code after a Location has been fetched by
 * {@link com.parse.ParseGeoPoint#getCurrentLocationInBackground(long, android.location.Criteria)}.
 * <p/>
 * The easiest way to use a {@code LocationCallback} is through an anonymous inner class. Override the
 * {@code done} function to specify what the callback should do after the location has been
 * fetched. The {@code done} function will be run in the UI thread, while the location check
 * happens in a background thread. This ensures that the UI does not freeze while the fetch happens.
 * <p/>
 * For example, this sample code defines a timeout for fetching the user's current location, and
 * provides a callback. Within the callback, the success and failure cases are handled differently.
 * <p/>
 * <pre>
 * ParseGeoPoint.getCurrentLocationAsync(1000, new LocationCallback() {
 *   public void done(ParseGeoPoint geoPoint, ParseException e) {
 *     if (e == null) {
 *       // do something with your new ParseGeoPoint
 *     } else {
 *       // handle your error
 *       e.printStackTrace();
 *     }
 *   }
 * });
 * </pre>
 */
public interface LocationCallback extends ParseCallback2<ParseGeoPoint, ParseException> {
    /**
     * Override this function with the code you want to run after the location fetch is complete.
     *
     * @param geoPoint The {@link ParseGeoPoint} returned by the location fetch.
     * @param e        The exception raised by the location fetch, or {@code null} if it succeeded.
     */
    @Override
    void done(ParseGeoPoint geoPoint, ParseException e);
}

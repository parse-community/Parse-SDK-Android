/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import android.os.Parcel;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class ParseGeoPointTest {

    @Test
    public void testConstructors() {
        ParseGeoPoint point = new ParseGeoPoint();
        assertEquals(0, point.getLatitude(), 0);
        assertEquals(0, point.getLongitude(), 0);

        double lat = 1.0;
        double lng = 2.0;
        point = new ParseGeoPoint(lat, lng);
        assertEquals(lat, point.getLatitude(), 0);
        assertEquals(lng, point.getLongitude(), 0);

        ParseGeoPoint copy = new ParseGeoPoint(point);
        assertEquals(lat, copy.getLatitude(), 0);
        assertEquals(lng, copy.getLongitude(), 0);
    }

    @Test
    public void testEquals() {
        ParseGeoPoint pointA = new ParseGeoPoint(30d, 50d);
        ParseGeoPoint pointB = new ParseGeoPoint(30d, 50d);
        ParseGeoPoint pointC = new ParseGeoPoint(45d, 45d);

        assertEquals(pointA, pointB);
        assertEquals(pointA, pointA);
        assertEquals(pointB, pointA);

        assertNotEquals(null, pointA);
        assertNotEquals(true, pointA);
        assertNotEquals(pointA, pointC);
    }

    @Test
    public void testParcelable() {
        ParseGeoPoint point = new ParseGeoPoint(30d, 50d);
        Parcel parcel = Parcel.obtain();
        point.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        point = ParseGeoPoint.CREATOR.createFromParcel(parcel);
        assertEquals(point.getLatitude(), 30d, 0);
        assertEquals(point.getLongitude(), 50d, 0);
    }
}

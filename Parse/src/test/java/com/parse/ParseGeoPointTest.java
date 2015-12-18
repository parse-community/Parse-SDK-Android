/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import org.junit.Test;

import static org.junit.Assert.*;

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
}

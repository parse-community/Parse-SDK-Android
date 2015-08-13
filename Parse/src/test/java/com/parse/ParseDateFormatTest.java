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

import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class ParseDateFormatTest {
  @Test
  public void testParse() {
    String string = "2015-05-13T11:08:01.123Z";
    Date date = ParseDateFormat.getInstance().parse(string);
    assertEquals(1431515281123L, date.getTime());
  }

  @Test
  public void testParseInvalid() {
    String string = "2015-05-13T11:08:01Z";
    Date date = ParseDateFormat.getInstance().parse(string);
    assertNull(date);
  }

  @Test
  public void testFormat() {
    Date date = new Date(1431515281123L);
    String string = ParseDateFormat.getInstance().format(date);
    assertEquals("2015-05-13T11:08:01.123Z", string);
  }
}

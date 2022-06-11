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
import static org.junit.Assert.assertNull;

import java.util.Date;
import org.junit.Test;

public class ParseImpreciseDateFormatTest {
    @Test
    public void testParse() {
        String string = "2015-05-13T11:08:01Z";
        Date date = ParseImpreciseDateFormat.getInstance().parse(string);
        assertEquals(1431515281000L, date.getTime());
    }

    @Test
    public void testParseInvalid() {
        String string = "2015-05-13T11:08:01.123Z";
        Date date = ParseImpreciseDateFormat.getInstance().parse(string);
        assertNull(date);
    }

    @Test
    public void testFormat() {
        Date date = new Date(1431515281000L);
        String string = ParseImpreciseDateFormat.getInstance().format(date);
        assertEquals("2015-05-13T11:08:01Z", string);
    }
}

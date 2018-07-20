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

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ParseTextUtilsTest {

    //region testJoin

    @Test
    public void testJoinMultipleItems() {
        String joined = ParseTextUtils.join(",", Arrays.asList("one", "two", "three"));
        assertEquals("one,two,three", joined);
    }

    @Test
    public void testJoinSingleItem() {
        String joined = ParseTextUtils.join(",", Collections.singletonList("one"));
        assertEquals("one", joined);
    }

    //endregion

    //region testIsEmpty

    @Test
    public void testEmptyStringIsEmpty() {
        assertTrue(ParseTextUtils.isEmpty(""));
    }

    @Test
    public void testNullStringIsEmpty() {
        assertTrue(ParseTextUtils.isEmpty(null));
    }

    @Test
    public void testStringIsNotEmpty() {
        assertFalse(ParseTextUtils.isEmpty("not empty"));
    }

    //endregion

    //region testEquals

    @Test
    public void testEqualsNull() {
        assertTrue(ParseTextUtils.equals(null, null));
    }

    @Test
    public void testNotEqualsNull() {
        assertFalse(ParseTextUtils.equals("not null", null));
        assertFalse(ParseTextUtils.equals(null, "not null"));
    }

    @Test
    public void testEqualsString() {
        String same = "Hello, world!";
        assertTrue(ParseTextUtils.equals(same, same));
        assertTrue(ParseTextUtils.equals(same, same + "")); // Hack to compare different instances
    }

    @Test
    public void testNotEqualsString() {
        assertFalse(ParseTextUtils.equals("grantland", "nlutsenko"));
    }

    //endregion
}

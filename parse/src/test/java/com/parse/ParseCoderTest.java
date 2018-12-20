/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;

// For android.util.Base64
@RunWith(RobolectricTestRunner.class)
@Config(constants = BuildConfig.class, sdk = TestHelper.ROBOLECTRIC_SDK_VERSION)
public class ParseCoderTest {

    @Test
    public void testBytes() {
        // string of bytes, including some invalid UTF8 data
        byte[] bytes = {4, 8, 16, 32, -128, 0, 0, 0};

        ParseEncoder encoder = PointerEncoder.get();
        JSONObject json = (JSONObject) encoder.encode(bytes);

        ParseDecoder decoder = ParseDecoder.get();
        byte[] bytesAgain = (byte[]) decoder.decode(json);

        assertEquals(8, bytesAgain.length);
        assertEquals(4, bytesAgain[0]);
        assertEquals(8, bytesAgain[1]);
        assertEquals(16, bytesAgain[2]);
        assertEquals(32, bytesAgain[3]);
        assertEquals(-128, bytesAgain[4]);
        assertEquals(0, bytesAgain[5]);
        assertEquals(0, bytesAgain[6]);
        assertEquals(0, bytesAgain[7]);
    }
}

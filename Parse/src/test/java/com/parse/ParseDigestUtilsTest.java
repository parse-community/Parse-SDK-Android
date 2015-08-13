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

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class ParseDigestUtilsTest {

  @Test
  public void testMD5() {
    Map<String, String> stringToMD5 = new HashMap<>();
    stringToMD5.put("grantland", "66ad19754cd2edcc20c3221a5488a599");
    stringToMD5.put("nikita", "b00a50c448238a71ed479f81fa4d9066");
    stringToMD5.put("1337", "e48e13207341b6bffb7fb1622282247b");
    stringToMD5.put("I am a potato", "2e832e16f60587842c7e4080142dbeca");

    for (Map.Entry<String, String> entry : stringToMD5.entrySet()) {
      String md5 = ParseDigestUtils.md5(entry.getKey());
      assertEquals(entry.getValue(), md5);
    }
  }
}

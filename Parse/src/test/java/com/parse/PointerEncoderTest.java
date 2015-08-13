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
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static org.junit.Assert.assertNotNull;

public class PointerEncoderTest {

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Test
  public void testEncodeRelatedObjectWithoutObjectId() {
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("unable to encode an association with an unsaved ParseObject");

    ParseObject parseObject = new ParseObject("TestObject");
    JSONObject jsonObject = (JSONObject) PointerEncoder.get().encode(parseObject);
  }

  @Test
  public void testEncodeRelatedObjectWithObjectId() {
    ParseObject parseObject = new ParseObject("TestObject");
    parseObject.setObjectId("1234");
    JSONObject jsonObject = (JSONObject) PointerEncoder.get().encode(parseObject);
    assertNotNull(jsonObject);
  }
}

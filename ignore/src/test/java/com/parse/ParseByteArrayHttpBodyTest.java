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

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class ParseByteArrayHttpBodyTest {

  @Test
  public void testInitializeWithString() throws IOException {
    String content = "content";
    String contentType = "application/json";
    ParseByteArrayHttpBody body = new ParseByteArrayHttpBody(content, contentType);
    assertArrayEquals(content.getBytes(), ParseIOUtils.toByteArray(body.getContent()));
    assertEquals(contentType, body.getContentType());
    assertEquals(7, body.getContentLength());
  }

  @Test
  public void testInitializeWithByteArray() throws IOException {
    byte[] content = {1, 1, 1, 1, 1};
    String contentType = "application/json";
    ParseByteArrayHttpBody body = new ParseByteArrayHttpBody(content, contentType);
    assertArrayEquals(content, ParseIOUtils.toByteArray(body.getContent()));
    assertEquals(contentType, body.getContentType());
    assertEquals(5, body.getContentLength());
  }

  @Test
  public void testWriteTo() throws IOException {
    String content = "content";
    String contentType = "application/json";
    ParseByteArrayHttpBody body = new ParseByteArrayHttpBody(content, contentType);

    // Check content
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    body.writeTo(output);
    String contentAgain = output.toString();
    assertEquals(content, contentAgain);

    // No need to check whether content input stream is closed since it is a ByteArrayInputStream
  }
}

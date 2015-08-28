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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class ParseHttpResponseTest {

  @Test
  public void testParseHttpResponseDefaults() throws IOException {
    ParseHttpResponse response = new ParseHttpResponse.Builder().build();

    assertNull(response.getContent());
    assertNull(response.getContentType());
    assertNull(response.getReasonPhrase());
    assertEquals(0, response.getStatusCode());
    assertEquals(0, response.getTotalSize());
    assertEquals(0, response.getAllHeaders().size());
    assertNull(response.getHeader("test"));
  }

  @Test
  public void testParseHttpResponseGetMethod() throws IOException {
    Map<String, String> headers = new HashMap<>();
    String name = "name";
    String value = "value";
    headers.put(name, value);
    String content = "content";
    String contentType = "application/json";
    String reasonPhase = "OK";
    int statusCode = 200;
    int totalSize = content.length();

    ParseHttpResponse response = new ParseHttpResponse.Builder()
        .setContent(new ByteArrayInputStream(content.getBytes()))
        .setContentType(contentType)
        .setHeaders(headers)
        .setReasonPhase(reasonPhase)
        .setStatusCode(statusCode)
        .setTotalSize(totalSize)
        .build();

    assertArrayEquals(content.getBytes(), ParseIOUtils.toByteArray(response.getContent()));
    assertEquals(contentType, response.getContentType());
    assertEquals(reasonPhase, response.getReasonPhrase());
    assertEquals(statusCode, response.getStatusCode());
    assertEquals(totalSize, response.getTotalSize());
    assertEquals(value, response.getHeader(name));
    assertEquals(1, response.getAllHeaders().size());
  }
}

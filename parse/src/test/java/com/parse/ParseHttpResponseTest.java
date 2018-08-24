/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import com.parse.http.ParseHttpResponse;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class ParseHttpResponseTest {

    @Test
    public void testParseHttpResponseDefaults() {
        ParseHttpResponse response = new ParseHttpResponse.Builder().build();

        assertNull(response.getContent());
        assertNull(response.getContentType());
        assertNull(response.getReasonPhrase());
        assertEquals(0, response.getStatusCode());
        assertEquals(-1, response.getTotalSize());
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
        String reasonPhrase = "OK";
        int statusCode = 200;
        int totalSize = content.length();

        ParseHttpResponse response = new ParseHttpResponse.Builder()
                .setContent(new ByteArrayInputStream(content.getBytes()))
                .setContentType(contentType)
                .setHeaders(headers)
                .setReasonPhrase(reasonPhrase)
                .setStatusCode(statusCode)
                .setTotalSize(totalSize)
                .build();

        assertArrayEquals(content.getBytes(), ParseIOUtils.toByteArray(response.getContent()));
        assertEquals(contentType, response.getContentType());
        assertEquals(reasonPhrase, response.getReasonPhrase());
        assertEquals(statusCode, response.getStatusCode());
        assertEquals(totalSize, response.getTotalSize());
        assertEquals(value, response.getHeader(name));
        assertEquals(1, response.getAllHeaders().size());
    }

    @Test
    public void testParseHttpResponseBuildWithParseHttpResponse() {
        Map<String, String> headers = new HashMap<>();
        String name = "name";
        String value = "value";
        headers.put(name, value);
        String content = "content";
        String contentType = "application/json";
        String reasonPhrase = "OK";
        int statusCode = 200;
        int totalSize = content.length();

        ParseHttpResponse response = new ParseHttpResponse.Builder()
                .setContent(new ByteArrayInputStream(content.getBytes()))
                .setContentType(contentType)
                .setHeaders(headers)
                .setReasonPhrase(reasonPhrase)
                .setStatusCode(statusCode)
                .setTotalSize(totalSize)
                .build();

        String newReasonPhrase = "Failed";
        ParseHttpResponse newResponse = new ParseHttpResponse.Builder(response)
                .setReasonPhrase(newReasonPhrase)
                .build();

        assertEquals(contentType, newResponse.getContentType());
        assertEquals(newReasonPhrase, newResponse.getReasonPhrase());
        assertEquals(statusCode, newResponse.getStatusCode());
        assertEquals(totalSize, newResponse.getTotalSize());
        assertEquals(value, newResponse.getHeader(name));
        assertEquals(1, newResponse.getAllHeaders().size());
        assertSame(response.getContent(), newResponse.getContent());
    }
}

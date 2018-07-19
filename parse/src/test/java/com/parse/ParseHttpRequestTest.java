/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import com.parse.http.ParseHttpBody;
import com.parse.http.ParseHttpRequest;

import org.junit.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class ParseHttpRequestTest {

    @Test
    public void testParseHttpRequestGetMethod() throws IOException {
        String url = "www.parse.com";
        ParseHttpRequest.Method method = ParseHttpRequest.Method.POST;
        Map<String, String> headers = new HashMap<>();
        String name = "name";
        String value = "value";
        headers.put(name, value);

        String content = "content";
        String contentType = "application/json";
        ParseByteArrayHttpBody body = new ParseByteArrayHttpBody(content, contentType);

        ParseHttpRequest request = new ParseHttpRequest.Builder()
                .setUrl(url)
                .addHeader(name, value)
                .setMethod(method)
                .setBody(body)
                .build();

        assertEquals(url, request.getUrl());
        assertEquals(method.toString(), request.getMethod().toString());
        assertEquals(1, request.getAllHeaders().size());
        assertEquals(value, request.getHeader(name));
        ParseHttpBody bodyAgain = request.getBody();
        assertEquals(contentType, bodyAgain.getContentType());
        assertArrayEquals(content.getBytes(), ParseIOUtils.toByteArray(body.getContent()));
    }

    @Test
    public void testParseHttpRequestBuilderInitialization() throws IOException {
        String url = "www.parse.com";
        ParseHttpRequest.Method method = ParseHttpRequest.Method.POST;
        Map<String, String> headers = new HashMap<>();
        String name = "name";
        String value = "value";
        headers.put(name, value);

        String content = "content";
        String contentType = "application/json";
        ParseByteArrayHttpBody body = new ParseByteArrayHttpBody(content, contentType);

        ParseHttpRequest request = new ParseHttpRequest.Builder()
                .setUrl(url)
                .addHeader(name, value)
                .setMethod(method)
                .setBody(body)
                .build();

        ParseHttpRequest requestAgain = new ParseHttpRequest.Builder(request).build();

        assertEquals(url, requestAgain.getUrl());
        assertEquals(method.toString(), requestAgain.getMethod().toString());
        assertEquals(1, requestAgain.getAllHeaders().size());
        assertEquals(value, requestAgain.getHeader(name));
        ParseHttpBody bodyAgain = requestAgain.getBody();
        assertEquals(contentType, bodyAgain.getContentType());
        assertArrayEquals(content.getBytes(), ParseIOUtils.toByteArray(body.getContent()));
    }

    @Test
    public void testParseHttpRequestBuildWithParseHttpRequest() throws IOException {
        String url = "www.parse.com";
        ParseHttpRequest.Method method = ParseHttpRequest.Method.POST;
        Map<String, String> headers = new HashMap<>();
        String name = "name";
        String value = "value";
        headers.put(name, value);

        String content = "content";
        String contentType = "application/json";
        ParseByteArrayHttpBody body = new ParseByteArrayHttpBody(content, contentType);

        ParseHttpRequest request = new ParseHttpRequest.Builder()
                .setUrl(url)
                .addHeader(name, value)
                .setMethod(method)
                .setBody(body)
                .build();

        String newURL = "www.api.parse.com";
        ParseHttpRequest newRequest = new ParseHttpRequest.Builder(request)
                .setUrl(newURL)
                .build();

        assertEquals(newURL, newRequest.getUrl());
        assertEquals(method.toString(), newRequest.getMethod().toString());
        assertEquals(1, newRequest.getAllHeaders().size());
        assertEquals(value, newRequest.getHeader(name));
        ParseHttpBody bodyAgain = newRequest.getBody();
        assertEquals(contentType, bodyAgain.getContentType());
        assertArrayEquals(content.getBytes(), ParseIOUtils.toByteArray(body.getContent()));
    }
}

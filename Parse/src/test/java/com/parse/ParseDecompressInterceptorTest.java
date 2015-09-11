/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */

package com.parse;

import com.parse.http.ParseHttpRequest;
import com.parse.http.ParseHttpResponse;
import com.parse.http.ParseNetworkInterceptor;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.annotation.Config;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

@RunWith(RobolectricGradleTestRunner.class)
@Config(constants = BuildConfig.class, sdk = 21)
public class ParseDecompressInterceptorTest {

  @Test
  public void testDecompressInterceptorWithNotGZIPResponse() throws Exception {
    ParseDecompressInterceptor interceptor = new ParseDecompressInterceptor();

    final String responseContent = "content";
    ParseHttpResponse interceptedResponse =
        interceptor.intercept(new ParseNetworkInterceptor.Chain() {
      @Override
      public ParseHttpRequest getRequest() {
        // Generate test request
        return new ParseHttpRequest.Builder()
            .setUrl("www.parse.com")
            .setMethod(ParseHttpRequest.Method.GET)
            .build();
      }

      @Override
      public ParseHttpResponse proceed(ParseHttpRequest request) throws IOException {
        // Generate test response
        return new ParseHttpResponse.Builder()
            .setStatusCode(200)
            .setTotalSize(responseContent.length())
            .setReasonPhrase("Success")
            .setContentType("text/plain")
            .setContent(new ByteArrayInputStream(responseContent.getBytes()))
            .build();
      }
    });

    // Verify response is correct
    assertEquals(200, interceptedResponse.getStatusCode());
    assertEquals(responseContent.length(), interceptedResponse.getTotalSize());
    assertEquals("Success", interceptedResponse.getReasonPhrase());
    assertEquals("text/plain", interceptedResponse.getContentType());
    byte[] content = ParseIOUtils.toByteArray(interceptedResponse.getContent());
    assertArrayEquals(responseContent.getBytes(), content);
  }

  @Test
  public void testDecompressInterceptorWithGZIPResponse() throws Exception {
    ParseDecompressInterceptor interceptor = new ParseDecompressInterceptor();

    final String responseContent = "content";
    ParseHttpResponse interceptedResponse =
        interceptor.intercept(new ParseNetworkInterceptor.Chain() {
      @Override
      public ParseHttpRequest getRequest() {
        // Generate test request
        return new ParseHttpRequest.Builder()
            .setUrl("www.parse.com")
            .setMethod(ParseHttpRequest.Method.GET)
            .build();
      }

      @Override
      public ParseHttpResponse proceed(ParseHttpRequest request) throws IOException {
        // Make gzip response content
        ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
        GZIPOutputStream gzipOut = new GZIPOutputStream(byteOut);
        gzipOut.write(responseContent.getBytes());
        gzipOut.close();
        // Make gzip encoding headers
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Encoding", "gzip");
        // Generate test response
        return new ParseHttpResponse.Builder()
            .setStatusCode(200)
            .setTotalSize(byteOut.toByteArray().length)
            .setReasonPhrase("Success")
            .setContentType("text/plain")
            .setContent(new ByteArrayInputStream(byteOut.toByteArray()))
            .setHeaders(headers)
            .build();
      }
    });

    // Verify response is correct
    assertEquals(200, interceptedResponse.getStatusCode());
    assertEquals(-1, interceptedResponse.getTotalSize());
    assertEquals("Success", interceptedResponse.getReasonPhrase());
    assertEquals("text/plain", interceptedResponse.getContentType());
    assertNull(interceptedResponse.getHeader("Content-Encoding"));
    byte[] content = ParseIOUtils.toByteArray(interceptedResponse.getContent());
    assertArrayEquals(responseContent.getBytes(), content);
  }
}

/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.annotation.Config;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPOutputStream;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

// For GZIPOutputStream
@RunWith(RobolectricGradleTestRunner.class)
@Config(constants = BuildConfig.class, sdk = 21)
public class ParseLogInterceptorTest {

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Test
  public void testInterceptNotGZIPResponse() throws Exception {
    ParseLogInterceptor interceptor = new ParseLogInterceptor();

    final String content = "content";
    final Semaphore done = new Semaphore(0);
    interceptor.setLogger(new ParseLogInterceptor.Logger() {
      @Override
      public void write(String str) {
      }

      @Override
      public void writeLine(String name, String value) {
       if ("Body".equals(name)) {
          assertEquals(content, value);
          done.release();
       }
      }
    });

    ParseHttpResponse response = interceptor.intercept(new ParseNetworkInterceptor.Chain() {
      @Override
      public ParseHttpRequest getRequest() {
        // We do not need request for this test so we simply return an empty request
        return new ParseHttpRequest.Builder().setMethod(ParseRequest.Method.GET).build();
      }

      @Override
      public ParseHttpResponse proceed(ParseHttpRequest request) throws IOException {
        ParseHttpResponse response = new ParseHttpResponse.Builder()
            .setContentType("application/json")
            .setContent(new ByteArrayInputStream(content.getBytes()))
            .build();
        return response;
      }
    });

    // Make sure the content we get from response is correct
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    ParseIOUtils.copy(response.getContent(), output);
    assertEquals(content, new String(output.toByteArray()));
    // Make sure we log the right content
    assertTrue(done.tryAcquire(10, TimeUnit.SECONDS));
  }

  @Test
  public void testInterceptGZIPResponse() throws Exception {
    ParseLogInterceptor interceptor = new ParseLogInterceptor();

    final String content = "content";
    final ByteArrayOutputStream gzipByteOutput = new ByteArrayOutputStream();
    GZIPOutputStream gzipOutput = new GZIPOutputStream(gzipByteOutput);
    gzipOutput.write(content.getBytes());
    gzipOutput.close();

    final Semaphore done = new Semaphore(0);
    interceptor.setLogger(new ParseLogInterceptor.Logger() {
      @Override
      public void write(String str) {
      }

      @Override
      public void writeLine(String name, String value) {
       if ("Body".equals(name)) {
          assertEquals(content, value);
          done.release();
       }
      }
    });

    ParseHttpResponse response = interceptor.intercept(new ParseNetworkInterceptor.Chain() {
      @Override
      public ParseHttpRequest getRequest() {
        // We do not need request for this test so we simply return an empty request
        return new ParseHttpRequest.Builder().setMethod(ParseRequest.Method.GET).build();
      }

      @Override
      public ParseHttpResponse proceed(ParseHttpRequest request) throws IOException {
        ParseHttpResponse response = new ParseHttpResponse.Builder()
            .addHeader("Content-Encoding", "gzip")
            .setContentType("application/json")
            .setContent(new ByteArrayInputStream(gzipByteOutput.toByteArray()))
            .build();
        return response;
      }
    });

    // Make sure the content we get from response is the gzip content
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    ParseIOUtils.copy(response.getContent(), output);
    assertArrayEquals(gzipByteOutput.toByteArray(), output.toByteArray());
    // Make sure we log the ungzip content
    assertTrue(done.tryAcquire(10, TimeUnit.SECONDS));
  }

  @Test
  public void testInterceptResponseWithException() throws Exception {
    ParseLogInterceptor interceptor = new ParseLogInterceptor();

    final String errorMessage = "error";
    final Semaphore done = new Semaphore(0);
    interceptor.setLogger(new ParseLogInterceptor.Logger() {
      @Override
      public void write(String str) {
      }

      @Override
      public void writeLine(String name, String value) {
       if ("Error".equals(name)) {
          assertEquals(errorMessage, value);
          done.release();
       }
      }
    });

    // Make sure the exception we get is correct
    thrown.expect(IOException.class);
    thrown.expectMessage(errorMessage);

    interceptor.intercept(new ParseNetworkInterceptor.Chain() {
      @Override
      public ParseHttpRequest getRequest() {
        // We do not need request for this test so we simply return an empty request
        return new ParseHttpRequest.Builder().setMethod(ParseRequest.Method.GET).build();
      }

      @Override
      public ParseHttpResponse proceed(ParseHttpRequest request) throws IOException {
        throw new IOException(errorMessage);
      }
    });

    // Make sure we log the right content
    assertTrue(done.tryAcquire(10, TimeUnit.SECONDS));
  }
}

/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import com.squareup.okhttp.Call;
import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.Protocol;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;
import com.squareup.okhttp.ResponseBody;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.annotation.Config;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import okio.Buffer;
import okio.BufferedSource;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@RunWith(RobolectricGradleTestRunner.class)
@Config(constants = BuildConfig.class, sdk = 21)
public class ParseOkHttpClientTest {

  @Test
  public void testGetOkHttpRequestType() throws IOException {
    ParseOkHttpClient parseClient = new ParseOkHttpClient(10000, null);
    ParseHttpRequest.Builder builder = new ParseHttpRequest.Builder();
    builder.setUrl("http://www.parse.com");

    // Get
    ParseHttpRequest parseRequest = builder
        .setMethod(ParseRequest.Method.GET)
        .setBody(null)
        .build();
    Request okHttpRequest = parseClient.getRequest(parseRequest);
    assertEquals(ParseRequest.Method.GET.toString(), okHttpRequest.method());

    // Post
    parseRequest = builder
        .setMethod(ParseRequest.Method.POST)
        .setBody(new ParseByteArrayHttpBody("test", "application/json"))
        .build();
    okHttpRequest = parseClient.getRequest(parseRequest);
    assertEquals(ParseRequest.Method.POST.toString(), okHttpRequest.method());

    // Delete
    parseRequest = builder
        .setMethod(ParseRequest.Method.DELETE)
        .setBody(null)
        .build();
    okHttpRequest = parseClient.getRequest(parseRequest);
    assertEquals(ParseRequest.Method.DELETE.toString(), okHttpRequest.method());

    // Put
    parseRequest = builder
        .setMethod(ParseRequest.Method.PUT)
        .setBody(new ParseByteArrayHttpBody("test", "application/json"))
        .build();
    okHttpRequest = parseClient.getRequest(parseRequest);
    assertEquals(ParseRequest.Method.PUT.toString(), okHttpRequest.method());
  }

  @Test
  public void testGetOkHttpRequest() throws IOException {
    Map<String, String> headers = new HashMap<>();
    String headerName = "name";
    String headerValue = "value";
    headers.put(headerName, headerValue);

    String url = "http://www.parse.com/";
    String content = "test";
    int contentLength = content.length();
    String contentType = "application/json";
    ParseHttpRequest parseRequest = new ParseHttpRequest.Builder()
        .setUrl(url)
        .setMethod(ParseRequest.Method.POST)
        .setBody(new ParseByteArrayHttpBody(content, contentType))
        .setHeaders(headers)
        .build();

    ParseOkHttpClient parseClient = new ParseOkHttpClient(10000, null);
    Request okHttpRequest = parseClient.getRequest(parseRequest);

    // Verify method
    assertEquals(ParseRequest.Method.POST.toString(), okHttpRequest.method());
    // Verify URL
    assertEquals(url, okHttpRequest.urlString());
    // Verify Headers
    assertEquals(1, okHttpRequest.headers(headerName).size());
    assertEquals(headerValue, okHttpRequest.headers(headerName).get(0));
    // Verify Body
    RequestBody okHttpBody = okHttpRequest.body();
    assertEquals(contentLength, okHttpBody.contentLength());
    assertEquals(contentType, okHttpBody.contentType().toString());
    // Can not read parseRequest body to compare since it has been read during
    // creating okHttpRequest
    Buffer buffer = new Buffer();
    okHttpBody.writeTo(buffer);
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    buffer.copyTo(output);
    assertArrayEquals(content.getBytes(), output.toByteArray());
  }

  @Test
  public void testGetOkHttpRequestWithEmptyContentType() throws Exception {
    String url = "http://www.parse.com/";
    String content = "test";
    ParseHttpRequest parseRequest = new ParseHttpRequest.Builder()
        .setUrl(url)
        .setMethod(ParseRequest.Method.POST)
        .setBody(new ParseByteArrayHttpBody(content, null))
        .build();

    ParseOkHttpClient parseClient = new ParseOkHttpClient(10000, null);
    Request okHttpRequest = parseClient.getRequest(parseRequest);

    // Verify Content-Type
    assertNull(okHttpRequest.body().contentType());
  }

  @Test
  public void testGetParseResponse() throws IOException {
    int statusCode = 200;
    String reasonPhrase = "test reason";
    final String content = "test";
    final int contentLength = content.length();
    final String contentType = "application/json";
    String url = "http://www.parse.com/";
    Request request = new Request.Builder()
        .url(url)
        .build();
    Response okHttpResponse = new Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(statusCode)
        .message(reasonPhrase)
        .body(new ResponseBody() {
          @Override
          public MediaType contentType() {
            return MediaType.parse(contentType);
          }

          @Override
          public long contentLength() throws IOException {
            return contentLength;
          }

          @Override
          public BufferedSource source() throws IOException {
            Buffer buffer = new Buffer();
            buffer.write(content.getBytes());
            return buffer;
          }
        })
        .build();

    ParseOkHttpClient parseClient = new ParseOkHttpClient(10000, null);
    ParseHttpResponse parseResponse = parseClient.getResponse(okHttpResponse);

    // Verify status code
    assertEquals(statusCode, parseResponse.getStatusCode());
    // Verify reason phase
    assertEquals(reasonPhrase, parseResponse.getReasonPhrase());
    // Verify content length
    assertEquals(contentLength, parseResponse.getTotalSize());
    // Verify content
    assertArrayEquals(content.getBytes(), ParseIOUtils.toByteArray(parseResponse.getContent()));
  }
}

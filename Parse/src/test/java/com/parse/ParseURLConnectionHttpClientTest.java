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

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.annotation.Config;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.verify;

// For android.net.SSLCertificateSocketFactory
@RunWith(RobolectricGradleTestRunner.class)
@Config(constants = BuildConfig.class, sdk = 21)
public class ParseURLConnectionHttpClientTest {

  @Test
  public void testGetURLConnectionType() throws IOException {
    ParseURLConnectionHttpClient parseClient = new ParseURLConnectionHttpClient(10000, null);
    ParseHttpRequest.Builder builder = new ParseHttpRequest.Builder();
    builder.setUrl("http://www.parse.com");

    // Get
    ParseHttpRequest parseRequest = builder
        .setMethod(ParseHttpRequest.Method.GET)
        .setBody(null)
        .build();
    HttpURLConnection connection = parseClient.getRequest(parseRequest);
    assertEquals(ParseHttpRequest.Method.GET.toString(), connection.getRequestMethod());

    // Post
    parseRequest = builder
        .setMethod(ParseHttpRequest.Method.POST)
        .setBody(new ParseByteArrayHttpBody("test", "application/json"))
        .build();
    connection = parseClient.getRequest(parseRequest);
    assertEquals(ParseHttpRequest.Method.POST.toString(), connection.getRequestMethod());

    // Delete
    parseRequest = builder
        .setMethod(ParseHttpRequest.Method.DELETE)
        .setBody(null)
        .build();
    connection = parseClient.getRequest(parseRequest);
    assertEquals(ParseHttpRequest.Method.DELETE.toString(), connection.getRequestMethod());

    // Put
    parseRequest = builder
        .setMethod(ParseHttpRequest.Method.PUT)
        .setBody(new ParseByteArrayHttpBody("test", "application/json"))
        .build();
    connection = parseClient.getRequest(parseRequest);
    assertEquals(ParseHttpRequest.Method.PUT.toString(), connection.getRequestMethod());
  }

  @Test
  public void testGetURLConnectionRequest() throws IOException {
    Map<String, String> headers = new HashMap<>();
    String headerName = "name";
    String headerValue = "value";
    headers.put(headerName, headerValue);

    String url = "http://www.parse.com";
    String content = "test";
    String contentType = "application/json";
    ParseHttpRequest parseRequest = new ParseHttpRequest.Builder()
        .setUrl(url)
        .setMethod(ParseHttpRequest.Method.POST)
        .setBody(new ParseByteArrayHttpBody(content, contentType))
        .setHeaders(headers)
        .build();

    ParseURLConnectionHttpClient parseClient = new ParseURLConnectionHttpClient(10000, null);
    HttpURLConnection connection = parseClient.getRequest(parseRequest);

    // Verify method
    assertEquals(ParseHttpRequest.Method.POST.toString(), connection.getRequestMethod());
    // Verify URL
    assertEquals(url, connection.getURL().toString());
    // Verify Headers
    assertEquals(headerValue, connection.getRequestProperty(headerName));
    // Verify Content-Type
    assertEquals(contentType, connection.getRequestProperty("Content-Type"));
    // For URLConnection, in order to set body, we have to open the connection. However, since we do
    // not do network operation in ParseHttpRequest.getRequest(), the request body actually has not
    // been set after ParseHttpRequest.getRequest(). Thus we can not verify request body and
    // content length here.
  }

  @Test
  public void testGetURLConnectionRequestWithEmptyContentType() throws Exception {
    String url = "http://www.parse.com/";
    String content = "test";
    ParseHttpRequest parseRequest = new ParseHttpRequest.Builder()
        .setUrl(url)
        .setMethod(ParseHttpRequest.Method.POST)
        .setBody(new ParseByteArrayHttpBody(content, null))
        .build();

    ParseURLConnectionHttpClient parseClient = new ParseURLConnectionHttpClient(10000, null);
    HttpURLConnection connection = parseClient.getRequest(parseRequest);

    // Verify Content-Type
    assertNull(connection.getRequestProperty("Content-Type"));
  }

  @Test
  public void testGetParseResponse() throws IOException {
    // Test normal response (status code < 400)
    int statusCode = 200;
    String reasonPhrase = "test reason";
    String content = "content";
    int contentLength = content.length();
    HttpURLConnection connection = Mockito.mock(HttpURLConnection.class);
    Mockito.when(connection.getResponseCode()).thenReturn(statusCode);
    Mockito.when(connection.getResponseMessage()).thenReturn(reasonPhrase);
    Mockito.when(connection.getContentLength()).thenReturn(contentLength);
    Mockito.when(connection.getInputStream())
        .thenReturn(new ByteArrayInputStream(content.getBytes()));

    ParseURLConnectionHttpClient parseClient = new ParseURLConnectionHttpClient(10000, null);
    ParseHttpResponse parseResponse = parseClient.getResponse(connection);

    // Verify status code
    assertEquals(statusCode, parseResponse.getStatusCode());
    // Verify reason phrase
    assertEquals(reasonPhrase, parseResponse.getReasonPhrase());
    // Verify content length
    assertEquals(contentLength, parseResponse.getTotalSize());
    // Verify content
    assertArrayEquals(content.getBytes(), ParseIOUtils.toByteArray(parseResponse.getContent()));

    // Test error response (status code >= 400)
    statusCode = 400;
    reasonPhrase = "error";
    content = "error";
    contentLength = content.length();
    connection = Mockito.mock(HttpURLConnection.class);
    Mockito.when(connection.getResponseCode()).thenReturn(statusCode);
    Mockito.when(connection.getResponseMessage()).thenReturn(reasonPhrase);
    Mockito.when(connection.getContentLength()).thenReturn(contentLength);
    Mockito.when(connection.getErrorStream())
        .thenReturn(new ByteArrayInputStream(content.getBytes()));

    parseClient = new ParseURLConnectionHttpClient(10000, null);
    parseResponse = parseClient.getResponse(connection);

    // Verify status code
    assertEquals(statusCode, parseResponse.getStatusCode());
    // Verify reason phrase
    assertEquals(reasonPhrase, parseResponse.getReasonPhrase());
    // Verify content length
    assertEquals(contentLength, parseResponse.getTotalSize());
    // Verify content
    assertArrayEquals(content.getBytes(), ParseIOUtils.toByteArray(parseResponse.getContent()));
  }
}

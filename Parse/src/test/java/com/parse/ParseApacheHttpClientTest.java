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

import org.apache.http.ProtocolVersion;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.StringEntity;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.message.BasicStatusLine;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.annotation.Config;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.verify;

// For android.net.SSLCertificateSocketFactory
@RunWith(RobolectricGradleTestRunner.class)
@Config(constants = BuildConfig.class, sdk = 21)
public class ParseApacheHttpClientTest {

  @Test
  public void tesGetApacheRequestType() throws IOException {
    ParseApacheHttpClient parseClient = new ParseApacheHttpClient(10000, null);
    ParseHttpRequest.Builder builder = new ParseHttpRequest.Builder();
    builder.setUrl("http://www.parse.com/");

    // Get
    ParseHttpRequest parseRequest = builder
        .setMethod(ParseHttpRequest.Method.GET)
        .setBody(null)
        .build();
    HttpUriRequest apacheRequest = parseClient.getRequest(parseRequest);
    assertTrue(apacheRequest instanceof HttpGet);

    // Post
    parseRequest = builder
        .setMethod(ParseHttpRequest.Method.POST)
        .setBody(new ParseByteArrayHttpBody("test", "application/json"))
        .build();
    apacheRequest = parseClient.getRequest(parseRequest);
    assertTrue(apacheRequest instanceof HttpPost);

    // Delete
    parseRequest = builder
        .setMethod(ParseHttpRequest.Method.DELETE)
        .setBody(null)
        .build();
    apacheRequest = parseClient.getRequest(parseRequest);
    assertTrue(apacheRequest instanceof HttpDelete);

    // Put
    parseRequest = builder
        .setMethod(ParseHttpRequest.Method.PUT)
        .setBody(new ParseByteArrayHttpBody("test", "application/json"))
        .build();
    apacheRequest = parseClient.getRequest(parseRequest);
    assertTrue(apacheRequest instanceof HttpPut);
  }

  @Test
  public void testGetApacheRequest() throws IOException {
    Map<String, String> headers = new HashMap<>();
    String headerValue = "value";
    headers.put("name", headerValue);

    String url = "http://www.parse.com/";
    String content = "test";
    String contentType = "application/json";
    ParseHttpRequest parseRequest = new ParseHttpRequest.Builder()
        .setUrl(url)
        .setMethod(ParseHttpRequest.Method.POST)
        .setBody(new ParseByteArrayHttpBody(content, contentType))
        .setHeaders(headers)
        .build();

    ParseApacheHttpClient parseClient = new ParseApacheHttpClient(10000, null);
    HttpUriRequest apacheRequest = parseClient.getRequest(parseRequest);

    // Verify method
    assertTrue(apacheRequest instanceof HttpPost);
    // Verify URL
    assertEquals(url, apacheRequest.getURI().toString());
    // Verify Headers
    // We add gzip header to apacheRequest which does not contain in ParseRequest
    assertEquals(2, apacheRequest.getAllHeaders().length);
    assertEquals(1, apacheRequest.getHeaders("name").length);
    assertEquals("name", apacheRequest.getHeaders("name")[0].getName());
    assertEquals(headerValue, apacheRequest.getHeaders("name")[0].getValue());
    // Verify Body
    HttpPost apachePostRequest = (HttpPost)apacheRequest;
    assertEquals(4, apachePostRequest.getEntity().getContentLength());
    assertEquals(contentType, apachePostRequest.getEntity().getContentType().getValue());
    // Can not read parseRequest body to compare since it has been read during
    // creating apacheRequest
    assertArrayEquals(content.getBytes(),
        ParseIOUtils.toByteArray(apachePostRequest.getEntity().getContent()));
  }

  @Test
  public void testGetApacheRequestWithEmptyContentType() throws Exception {
    String url = "http://www.parse.com/";
    String content = "test";
    ParseHttpRequest parseRequest = new ParseHttpRequest.Builder()
        .setUrl(url)
        .setMethod(ParseHttpRequest.Method.POST)
        .setBody(new ParseByteArrayHttpBody(content, null))
        .build();

    ParseApacheHttpClient parseClient = new ParseApacheHttpClient(10000, null);
    HttpUriRequest apacheRequest = parseClient.getRequest(parseRequest);

    // Verify Content-Type
    HttpPost apachePostRequest = (HttpPost)apacheRequest;
    assertNull(apachePostRequest.getEntity().getContentType());
  }

  @Test
  public void testGetParseResponse() throws IOException {
    int statusCode = 200;
    String reasonPhrase = "test reason";
    ProtocolVersion protocol = new ProtocolVersion("HTTP", 1, 1);
    BasicStatusLine line = new BasicStatusLine(protocol, statusCode, reasonPhrase);
    BasicHttpResponse apacheResponse = new BasicHttpResponse(line);
    String content = "content";
    StringEntity entity = new StringEntity(content);
    apacheResponse.setEntity(entity);
    apacheResponse.setHeader("Content-Length", String.valueOf(entity.getContentLength()));

    ParseApacheHttpClient parseClient = new ParseApacheHttpClient(10000, null);
    ParseHttpResponse parseResponse = parseClient.getResponse(apacheResponse);

    // Verify status code
    assertEquals(statusCode, parseResponse.getStatusCode());
    // Verify reason phrase
    assertEquals(reasonPhrase, parseResponse.getReasonPhrase());
    // Verify content length
    assertEquals(7, parseResponse.getTotalSize());
    // Verify content
    // Can not read apacheResponse entity to compare since it has been read during
    // creating parseResponse
    assertArrayEquals(content.getBytes(), ParseIOUtils.toByteArray(parseResponse.getContent()));
  }
}

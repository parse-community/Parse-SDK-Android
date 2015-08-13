/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import com.squareup.okhttp.Headers;
import com.squareup.okhttp.mockwebserver.MockResponse;
import com.squareup.okhttp.mockwebserver.MockWebServer;
import com.squareup.okhttp.mockwebserver.RecordedRequest;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.annotation.Config;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricGradleTestRunner.class)
@Config(constants = BuildConfig.class, sdk = 21)
public class ParseHttpClientTest {

  // We can not use ParameterizedRobolectricTestRunner right now since Robolectric use
  // default java classloader when we construct the parameters. However
  // SSLCertificateSocketFactory is only mocked under Robolectric classloader.

  @Test
  public void testParseApacheHttpClientExecuteWithSuccessResponse() throws Exception {
    doSingleParseHttpClientExecuteWithResponse(200, "OK", "Success",
        new ParseApacheHttpClient(10000, null));
  }

  @Test
  public void testParseURLConnectionHttpClientExecuteWithSuccessResponse() throws Exception {
    doSingleParseHttpClientExecuteWithResponse(200, "OK", "Success",
        new ParseApacheHttpClient(10000, null));  }

  @Test
  public void testParseOkHttpClientExecuteWithSuccessResponse() throws Exception {
    doSingleParseHttpClientExecuteWithResponse(200, "OK", "Success",
        new ParseApacheHttpClient(10000, null));  }

  @Test
  public void testParseApacheHttpClientExecuteWithErrorResponse() throws Exception {
    doSingleParseHttpClientExecuteWithResponse(404, "NOT FOUND", "Error",
        new ParseApacheHttpClient(10000, null));  }

  @Test
  public void testParseURLConnectionHttpClientExecuteWithErrorResponse() throws Exception {
    doSingleParseHttpClientExecuteWithResponse(404, "NOT FOUND", "Error",
        new ParseURLConnectionHttpClient(10000, null));  }

  @Test
  public void testParseOkHttpClientExecuteWithErrorResponse() throws Exception {
    doSingleParseHttpClientExecuteWithResponse(404, "NOT FOUND", "Error",
        new ParseOkHttpClient(10000, null));  }

  private void doSingleParseHttpClientExecuteWithResponse(int responseCode, String responseStatus,
      String responseContent, ParseHttpClient client) throws Exception {
    MockWebServer server = new MockWebServer();

    // Make mock response
    int responseContentLength = responseContent.length();
    MockResponse mockResponse = new MockResponse()
        .setStatus("HTTP/1.1 " + responseCode + " " + responseStatus)
        .setBody(responseContent);

    // Start mock server
    server.enqueue(mockResponse);
    server.start();

    // Make ParseHttpRequest
    Map<String, String> requestHeaders = new HashMap<>();
    requestHeaders.put("User-Agent", "Parse Android SDK");

    String requestUrl = server.getUrl("/").toString();
    JSONObject json = new JSONObject();
    json.put("key", "value");
    String requestContent = json.toString();
    int requestContentLength = requestContent.length();
    String requestContentType = "application/json";
    ParseHttpRequest parseRequest = new ParseHttpRequest.Builder()
        .setUrl(requestUrl)
        .setMethod(ParseRequest.Method.POST)
        .setBody(new ParseByteArrayHttpBody(requestContent, requestContentType))
        .setHeaders(requestHeaders)
        .build();

    // Execute request
    ParseHttpResponse parseResponse = client.execute(parseRequest);

    RecordedRequest recordedApacheRequest = server.takeRequest();

    // Verify request method
    assertEquals(ParseRequest.Method.POST.toString(), recordedApacheRequest.getMethod());

    // Verify request headers, since http library automatically adds some headers, we only need to
    // verify all parseRequest headers are in recordedRequest headers.
    Headers recordedApacheHeaders = recordedApacheRequest.getHeaders();
    Set<String> recordedApacheHeadersNames = recordedApacheHeaders.names();
    for (String name : parseRequest.getAllHeaders().keySet()) {
      assertTrue(recordedApacheHeadersNames.contains(name));
      assertEquals(parseRequest.getAllHeaders().get(name), recordedApacheHeaders.get(name));
    }

    // Verify request body
    assertEquals(requestContentLength, recordedApacheRequest.getBodySize());
    assertArrayEquals(requestContent.getBytes(), recordedApacheRequest.getBody().readByteArray());

    // Verify response status code
    assertEquals(responseCode, parseResponse.getStatusCode());
    // Verify response status
    assertEquals(responseStatus, parseResponse.getReasonPhrase());
    // Verify all response header entries' keys and values are not null.
    for (Map.Entry<String, String> entry : parseResponse.getAllHeaders().entrySet()) {
      assertNotNull(entry.getKey());
      assertNotNull(entry.getValue());
    }
    // Verify response body
    byte[] content = ParseIOUtils.toByteArray(parseResponse.getContent());
    assertArrayEquals(responseContent.getBytes(), content);
    // Verify response body size
    assertEquals(responseContentLength, content.length);

    // Shutdown mock server
    server.shutdown();
  }
}

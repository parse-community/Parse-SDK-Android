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

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.annotation.Config;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPOutputStream;

import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okio.Buffer;
import okio.BufferedSource;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricGradleTestRunner.class)
@Config(constants = BuildConfig.class, sdk = 21)
public class ParseOkHttpClientTest {

  private MockWebServer server = new MockWebServer();

  //region testTransferRequest/Response

  @Test
  public void testGetOkHttpRequestType() throws IOException {
    ParseOkHttpClient parseClient = new ParseOkHttpClient(10000, null);
    ParseHttpRequest.Builder builder = new ParseHttpRequest.Builder();
    builder.setUrl("http://www.parse.com");

    // Get
    ParseHttpRequest parseRequest = builder
        .setMethod(ParseHttpRequest.Method.GET)
        .setBody(null)
        .build();
    Request okHttpRequest = parseClient.getRequest(parseRequest);
    assertEquals(ParseHttpRequest.Method.GET.toString(), okHttpRequest.method());

    // Post
    parseRequest = builder
        .setMethod(ParseHttpRequest.Method.POST)
        .setBody(new ParseByteArrayHttpBody("test", "application/json"))
        .build();
    okHttpRequest = parseClient.getRequest(parseRequest);
    assertEquals(ParseHttpRequest.Method.POST.toString(), okHttpRequest.method());

    // Delete
    parseRequest = builder
        .setMethod(ParseHttpRequest.Method.DELETE)
        .setBody(null)
        .build();
    okHttpRequest = parseClient.getRequest(parseRequest);
    assertEquals(ParseHttpRequest.Method.DELETE.toString(), okHttpRequest.method());

    // Put
    parseRequest = builder
        .setMethod(ParseHttpRequest.Method.PUT)
        .setBody(new ParseByteArrayHttpBody("test", "application/json"))
        .build();
    okHttpRequest = parseClient.getRequest(parseRequest);
    assertEquals(ParseHttpRequest.Method.PUT.toString(), okHttpRequest.method());
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
        .setMethod(ParseHttpRequest.Method.POST)
        .setBody(new ParseByteArrayHttpBody(content, contentType))
        .setHeaders(headers)
        .build();

    ParseOkHttpClient parseClient = new ParseOkHttpClient(10000, null);
    Request okHttpRequest = parseClient.getRequest(parseRequest);

    // Verify method
    assertEquals(ParseHttpRequest.Method.POST.toString(), okHttpRequest.method());
    // Verify URL
    assertEquals(url, okHttpRequest.url().toString());
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
        .setMethod(ParseHttpRequest.Method.POST)
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
          public long contentLength() {
            return contentLength;
          }

          @Override
          public BufferedSource source() {
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
    // Verify reason phrase
    assertEquals(reasonPhrase, parseResponse.getReasonPhrase());
    // Verify content length
    assertEquals(contentLength, parseResponse.getTotalSize());
    // Verify content
    assertArrayEquals(content.getBytes(), ParseIOUtils.toByteArray(parseResponse.getContent()));
  }

  //endregion

  //region testOkHttpClientWithInterceptor

  @Test
  public void testParseOkHttpClientExecuteWithInternalInterceptor() throws Exception {
    testParseOkHttpClientExecuteWithInterceptor(true);
  }

  @Test
  public void testParseOkHttpClientExecuteWithExternalInterceptor() throws Exception {
    testParseOkHttpClientExecuteWithInterceptor(false);
  }

  @Test
  public void testParseOkHttpClientExecuteWithExternalInterceptorAndGZIPResponse() throws Exception {
    // Make mock response
    Buffer buffer = new Buffer();
    final ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
    GZIPOutputStream gzipOut = new GZIPOutputStream(byteOut);
    gzipOut.write("content".getBytes());
    gzipOut.close();
    buffer.write(byteOut.toByteArray());
    MockResponse mockResponse = new MockResponse()
        .setStatus("HTTP/1.1 " + 201 + " " + "OK")
        .setBody(buffer)
        .setHeader("Content-Encoding", "gzip");

    // Start mock server
    server.enqueue(mockResponse);
    server.start();

    ParseHttpClient client = new ParseOkHttpClient(10000, null);

    final Semaphore done = new Semaphore(0);
    // Add plain interceptor to disable decompress response stream
    client.addExternalInterceptor(new ParseNetworkInterceptor() {
      @Override
      public ParseHttpResponse intercept(Chain chain) throws IOException {
        done.release();
        ParseHttpResponse parseResponse =  chain.proceed(chain.getRequest());
        // Make sure the response we get from the interceptor is the raw gzip stream
        byte[] content = ParseIOUtils.toByteArray(parseResponse.getContent());
        assertArrayEquals(byteOut.toByteArray(), content);

        // We need to set a new stream since we have read it
        return new ParseHttpResponse.Builder()
            .setContent(new ByteArrayInputStream(byteOut.toByteArray()))
            .build();
      }
    });

    // We do not need to add Accept-Encoding header manually, httpClient library should do that.
    String requestUrl = server.url("/").toString();
    ParseHttpRequest parseRequest = new ParseHttpRequest.Builder()
        .setUrl(requestUrl)
        .setMethod(ParseHttpRequest.Method.GET)
        .build();

    // Execute request
    ParseHttpResponse parseResponse = client.execute(parseRequest);

    // Make sure the response we get is ungziped by OkHttp library
    byte[] content = ParseIOUtils.toByteArray(parseResponse.getContent());
    assertArrayEquals("content".getBytes(), content);
    // Make sure interceptor is called
    assertTrue(done.tryAcquire(10, TimeUnit.SECONDS));

    server.shutdown();
  }

  // This test is used to test okHttp interceptors. The difference between external and
  // internal interceptor is the external interceptor is added to OkHttpClient level, an internal
  // interceptor is added to ParseHttpClient level.
  // In the interceptor, we change request and response to see whether our server and
  // ParseHttpClient can receive the correct value.
  private void testParseOkHttpClientExecuteWithInterceptor(
      boolean isInternalInterceptorTest) throws Exception {
    // Start mock server
    server.enqueue(generateServerResponse());
    server.start();

    ParseHttpClient client = new ParseOkHttpClient(10000, null);

    // Make ParseHttpRequest
    ParseHttpRequest parseRequest = generateClientRequest();

    final Semaphore done = new Semaphore(0);
    ParseNetworkInterceptor interceptor = new ParseNetworkInterceptor() {
      @Override
      public ParseHttpResponse intercept(Chain chain) throws IOException {
        done.release();

        ParseHttpRequest request = chain.getRequest();

        // Verify original request
        verifyClientRequest(request);

        // Change request
        ParseHttpRequest requestAgain = generateInterceptorRequest();

        // Proceed
        ParseHttpResponse parseResponse = chain.proceed(requestAgain);

        // Verify original response
        verifyServerResponse(parseResponse);

        // Change response
        return generateInterceptorResponse();
      }
    };

    // Add interceptor
    if (isInternalInterceptorTest) {
      client.addInternalInterceptor(interceptor);
    } else {
      client.addExternalInterceptor(interceptor);
    }

    // Execute request
    ParseHttpResponse parseResponse = client.execute(parseRequest);

    // Make sure interceptor is called
    assertTrue(done.tryAcquire(5, TimeUnit.SECONDS));

    RecordedRequest recordedRequest = server.takeRequest();
    // Verify request changed by interceptor
    verifyInterceptorRequest(recordedRequest);

    // Verify response changed by interceptor
    verifyInterceptorResponse(parseResponse);
  }

  // Generate a mocked Server response
  private MockResponse generateServerResponse() {
    MockResponse mockServerResponse = new MockResponse()
        .setStatus("HTTP/1.1 " + 200 + " " + "OK")
        .setBody("Success")
        .setHeader("responseKey", "responseValue");
    return mockServerResponse;
  }

  // Verify the mocked server response, if you change the data in generateServerResponse, make
  // sure you also change the condition in this method otherwise tests will fail
  private void verifyServerResponse(ParseHttpResponse parseResponse) throws IOException {
    assertEquals(200, parseResponse.getStatusCode());
    assertEquals("OK", parseResponse.getReasonPhrase());
    assertEquals("responseValue", parseResponse.getHeader("responseKey"));
    byte[] content = ParseIOUtils.toByteArray(parseResponse.getContent());
    assertArrayEquals("Success".getBytes(), content);
    assertEquals(7, content.length);
  }

  // Generate a ParseHttpRequest sent to server
  private ParseHttpRequest generateClientRequest() throws Exception {
    Map<String, String> headers = new HashMap<>();
    headers.put("requestkey", "requestValue");
    JSONObject json = new JSONObject();
    json.put("key", "value");
    ParseHttpRequest parseRequest = new ParseHttpRequest.Builder()
        .setUrl(server.url("/").toString())
        .setMethod(ParseHttpRequest.Method.POST)
        .setBody(new ParseByteArrayHttpBody(json.toString().getBytes(), "application/json"))
        .setHeaders(headers)
        .build();
    return parseRequest;
  }

  // Verify the request from client, if you change the data in generateClientRequest, make
  // sure you also change the condition in this method otherwise tests will fail
  private void verifyClientRequest(ParseHttpRequest parseRequest) throws IOException {
    assertEquals(server.url("/").toString(), parseRequest.getUrl());
    assertEquals(ParseHttpRequest.Method.POST, parseRequest.getMethod());
    assertEquals("requestValue", parseRequest.getHeader("requestkey"));
    assertEquals("application/json", parseRequest.getBody().getContentType());
    JSONObject json = new JSONObject();
    try {
      json.put("key", "value");
    } catch (JSONException e) {
      // do no
    }
    assertArrayEquals(
        json.toString().getBytes(),
        ParseIOUtils.toByteArray(parseRequest.getBody().getContent()));
  }

  // Generate a ParseHttpRequest sent from interceptor
  private ParseHttpRequest generateInterceptorRequest() {
    ParseHttpRequest requestAgain =
        new ParseHttpRequest.Builder()
            .addHeader("requestKeyAgain", "requestValueAgain")
            .setUrl(server.url("/test").toString())
            .setMethod(ParseHttpRequest.Method.GET)
            .build();
    return requestAgain;
  }

  // Verify the request from interceptor, if you change the data in generateInterceptorRequest, make
  // sure you also change the condition in this method otherwise tests will fail
  private void verifyInterceptorRequest(RecordedRequest recordedRequest) throws IOException {
    assertEquals("/test", recordedRequest.getPath());
    assertEquals(ParseHttpRequest.Method.GET.toString(), recordedRequest.getMethod());
    assertEquals("requestValueAgain", recordedRequest.getHeader("requestKeyAgain"));
  }

  // Generate a ParseHttpResponse returned from interceptor
  private ParseHttpResponse generateInterceptorResponse() {
    final String newResponseHeaderKey = "responseKey";
    final String newResponseHeaderValue = "responseValue";
    final Map<String, String> newResponseHeaders = new HashMap<>();
    newResponseHeaders.put(newResponseHeaderKey, newResponseHeaderValue);
    return new ParseHttpResponse.Builder()
        .setStatusCode(201)
        .setReasonPhrase("Fine")
        .setContent(new ByteArrayInputStream("content".getBytes()))
        .setTotalSize("content".length())
        .setHeaders(newResponseHeaders)
        .build();
  }

  // Verify the response from interceptor, if you change the data in generateInterceptorResponse,
  // make sure you also change the condition in this method otherwise tests will fail
  private void verifyInterceptorResponse(ParseHttpResponse parseResponse)
      throws IOException {
    assertEquals(201, parseResponse.getStatusCode());
    assertEquals("Fine", parseResponse.getReasonPhrase());
    assertEquals("responseValue", parseResponse.getHeader("responseKey"));
    byte[] content = ParseIOUtils.toByteArray(parseResponse.getContent());
    assertArrayEquals("content".getBytes(), content);
    assertEquals("content".length(), content.length);
  }

  //endregion
}

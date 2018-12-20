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
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okio.Buffer;
import okio.BufferedSource;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

@RunWith(RobolectricTestRunner.class)
@Config(constants = BuildConfig.class, sdk = TestHelper.ROBOLECTRIC_SDK_VERSION)
public class ParseOkHttpClientTest {

    private MockWebServer server = new MockWebServer();

    //region testTransferRequest/Response

    @Test
    public void testGetOkHttpRequestType() throws IOException {
        ParseHttpClient parseClient = ParseHttpClient.createClient(new OkHttpClient.Builder());
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
        assertEquals(null, okHttpRequest.body());

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

        ParseHttpClient parseClient = ParseHttpClient.createClient(new OkHttpClient.Builder());
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

        ParseHttpClient parseClient = ParseHttpClient.createClient(new OkHttpClient.Builder());
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

        ParseHttpClient parseClient = ParseHttpClient.createClient(new OkHttpClient.Builder());
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
    public void testParseOkHttpClientExecuteWithGZIPResponse() throws Exception {
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

        ParseHttpClient client = ParseHttpClient.createClient(new OkHttpClient.Builder());

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

        server.shutdown();
    }

    //endregion
}

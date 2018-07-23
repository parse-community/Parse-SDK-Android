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

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPOutputStream;

import okhttp3.Headers;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okio.Buffer;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(constants = BuildConfig.class, sdk = TestHelper.ROBOLECTRIC_SDK_VERSION)
public class ParseHttpClientTest {

    // We can not use ParameterizedRobolectricTestRunner right now since Robolectric use
    // default java classloader when we construct the parameters. However
    // SSLCertificateSocketFactory is only mocked under Robolectric classloader.

    @Test
    public void testParseOkHttpClientExecuteWithSuccessResponse() throws Exception {
        doSingleParseHttpClientExecuteWithResponse(
                200, "OK", "Success", ParseHttpClient.createClient(new OkHttpClient.Builder()));
    }

    @Test
    public void testParseOkHttpClientExecuteWithErrorResponse() throws Exception {
        doSingleParseHttpClientExecuteWithResponse(
                404, "NOT FOUND", "Error", ParseHttpClient.createClient(new OkHttpClient.Builder()));
    }

    // TODO(mengyan): Add testParseURLConnectionHttpClientExecuteWithGzipResponse, right now we can
    // not do that since in unit test env, URLConnection does not use OKHttp internally, so there is
    // no transparent ungzip

    @Test
    public void testParseOkHttpClientExecuteWithGzipResponse() throws Exception {
        doSingleParseHttpClientExecuteWithGzipResponse(
                200, "OK", "Success", ParseHttpClient.createClient(new OkHttpClient.Builder()));
    }

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

        String requestUrl = server.url("/").toString();
        JSONObject json = new JSONObject();
        json.put("key", "value");
        String requestContent = json.toString();
        int requestContentLength = requestContent.length();
        String requestContentType = "application/json";
        ParseHttpRequest parseRequest = new ParseHttpRequest.Builder()
                .setUrl(requestUrl)
                .setMethod(ParseHttpRequest.Method.POST)
                .setBody(new ParseByteArrayHttpBody(requestContent, requestContentType))
                .setHeaders(requestHeaders)
                .build();

        // Execute request
        ParseHttpResponse parseResponse = client.execute(parseRequest);

        RecordedRequest recordedApacheRequest = server.takeRequest();

        // Verify request method
        assertEquals(ParseHttpRequest.Method.POST.toString(), recordedApacheRequest.getMethod());

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

    private void doSingleParseHttpClientExecuteWithGzipResponse(
            int responseCode, String responseStatus, final String responseContent, ParseHttpClient client)
            throws Exception {
        MockWebServer server = new MockWebServer();

        // Make mock response
        Buffer buffer = new Buffer();
        ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
        GZIPOutputStream gzipOut = new GZIPOutputStream(byteOut);
        gzipOut.write(responseContent.getBytes());
        gzipOut.close();
        buffer.write(byteOut.toByteArray());
        MockResponse mockResponse = new MockResponse()
                .setStatus("HTTP/1.1 " + responseCode + " " + responseStatus)
                .setBody(buffer)
                .setHeader("Content-Encoding", "gzip");

        // Start mock server
        server.enqueue(mockResponse);
        server.start();

        // We do not need to add Accept-Encoding header manually, httpClient library should do that.
        String requestUrl = server.url("/").toString();
        ParseHttpRequest parseRequest = new ParseHttpRequest.Builder()
                .setUrl(requestUrl)
                .setMethod(ParseHttpRequest.Method.GET)
                .build();

        // Execute request
        ParseHttpResponse parseResponse = client.execute(parseRequest);

        RecordedRequest recordedRequest = server.takeRequest();

        // Verify request method
        assertEquals(ParseHttpRequest.Method.GET.toString(), recordedRequest.getMethod());

        // Verify request headers
        Headers recordedHeaders = recordedRequest.getHeaders();

        assertEquals("gzip", recordedHeaders.get("Accept-Encoding"));

        // Verify we do not have Content-Encoding header
        assertNull(parseResponse.getHeader("Content-Encoding"));

        // Verify response body
        byte[] content = ParseIOUtils.toByteArray(parseResponse.getContent());
        assertArrayEquals(responseContent.getBytes(), content);

        // Shutdown mock server
        server.shutdown();
    }
}

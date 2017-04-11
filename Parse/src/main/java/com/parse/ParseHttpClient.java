/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import android.support.annotation.Nullable;

import com.parse.http.ParseHttpBody;
import com.parse.http.ParseHttpRequest;
import com.parse.http.ParseHttpResponse;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSink;

class ParseHttpClient {

  private final static String OKHTTP_GET = "GET";
  private final static String OKHTTP_POST = "POST";
  private final static String OKHTTP_PUT = "PUT";
  private final static String OKHTTP_DELETE = "DELETE";

  public static ParseHttpClient createClient(@Nullable OkHttpClient.Builder builder) {
    return new ParseHttpClient(builder);
  }

  private static final String MAX_CONNECTIONS_PROPERTY_NAME = "http.maxConnections";
  private static final String KEEP_ALIVE_PROPERTY_NAME = "http.keepAlive";

  public static void setMaxConnections(int maxConnections) {
    if (maxConnections <= 0) {
      throw new IllegalArgumentException("Max connections should be large than 0");
    }
    System.setProperty(MAX_CONNECTIONS_PROPERTY_NAME, String.valueOf(maxConnections));
  }

  public static void setKeepAlive(boolean isKeepAlive) {
    System.setProperty(KEEP_ALIVE_PROPERTY_NAME, String.valueOf(isKeepAlive));
  }

  private OkHttpClient okHttpClient;
  private boolean hasExecuted;

  ParseHttpClient(@Nullable OkHttpClient.Builder builder) {

    if (builder == null) {
      builder = new OkHttpClient.Builder();
    }

    // Don't handle redirects. We copy the setting from AndroidHttpClient.
    // For detail, check https://quip.com/Px8jAxnaun2r
    builder.followRedirects(false);

    okHttpClient = builder.build();
  }

  public final ParseHttpResponse execute(ParseHttpRequest request) throws IOException {
    if (!hasExecuted) {
      hasExecuted = true;
    }
    return executeInternal(request);
  }

  ParseHttpResponse executeInternal(ParseHttpRequest parseRequest) throws IOException {
    Request okHttpRequest = getRequest(parseRequest);
    Call okHttpCall = okHttpClient.newCall(okHttpRequest);

    Response okHttpResponse = okHttpCall.execute();

    return getResponse(okHttpResponse);
  }

  ParseHttpResponse getResponse(Response okHttpResponse)
          throws IOException {
    // Status code
    int statusCode = okHttpResponse.code();

    // Content
    InputStream content = okHttpResponse.body().byteStream();

    // Total size
    int totalSize = (int) okHttpResponse.body().contentLength();

    // Reason phrase
    String reasonPhrase = okHttpResponse.message();

    // Headers
    Map<String, String> headers = new HashMap<>();
    for (String name : okHttpResponse.headers().names()) {
      headers.put(name, okHttpResponse.header(name));
    }

    // Content type
    String contentType = null;
    ResponseBody body = okHttpResponse.body();
    if (body != null && body.contentType() != null) {
      contentType = body.contentType().toString();
    }

    return new ParseHttpResponse.Builder()
            .setStatusCode(statusCode)
            .setContent(content)
            .setTotalSize(totalSize)
            .setReasonPhrase(reasonPhrase)
            .setHeaders(headers)
            .setContentType(contentType)
            .build();
  }

  Request getRequest(ParseHttpRequest parseRequest) throws IOException {
    Request.Builder okHttpRequestBuilder = new Request.Builder();
    ParseHttpRequest.Method method = parseRequest.getMethod();
    // Set method
    switch (method) {
      case GET:
        okHttpRequestBuilder.get();
        break;
      case DELETE:
      case POST:
      case PUT:
        // Since we need to set body and method at the same time for DELETE, POST, PUT, we will do it in
        // the following.
        break;
      default:
        // This case will never be reached since we have already handled this case in
        // ParseRequest.newRequest().
        throw new IllegalStateException("Unsupported http method " + method.toString());
    }
    // Set url
    okHttpRequestBuilder.url(parseRequest.getUrl());

    // Set Header
    Headers.Builder okHttpHeadersBuilder = new Headers.Builder();
    for (Map.Entry<String, String> entry : parseRequest.getAllHeaders().entrySet()) {
      okHttpHeadersBuilder.add(entry.getKey(), entry.getValue());
    }
    // OkHttp automatically add gzip header so we do not need to deal with it
    Headers okHttpHeaders = okHttpHeadersBuilder.build();
    okHttpRequestBuilder.headers(okHttpHeaders);

    // Set Body
    ParseHttpBody parseBody = parseRequest.getBody();
    ParseOkHttpRequestBody okHttpRequestBody = null;
    if (parseBody != null) {
      okHttpRequestBody = new ParseOkHttpRequestBody(parseBody);
    }
    switch (method) {
      case PUT:
        okHttpRequestBuilder.put(okHttpRequestBody);
        break;
      case POST:
        okHttpRequestBuilder.post(okHttpRequestBody);
        break;
      case DELETE:
        okHttpRequestBuilder.delete(okHttpRequestBody);
    }
    return okHttpRequestBuilder.build();
  }

  private ParseHttpRequest getParseHttpRequest(Request okHttpRequest) {
    ParseHttpRequest.Builder parseRequestBuilder = new ParseHttpRequest.Builder();
    // Set method
    switch (okHttpRequest.method()) {
      case OKHTTP_GET:
        parseRequestBuilder.setMethod(ParseHttpRequest.Method.GET);
        break;
      case OKHTTP_DELETE:
        parseRequestBuilder.setMethod(ParseHttpRequest.Method.DELETE);
        break;
      case OKHTTP_POST:
        parseRequestBuilder.setMethod(ParseHttpRequest.Method.POST);
        break;
      case OKHTTP_PUT:
        parseRequestBuilder.setMethod(ParseHttpRequest.Method.PUT);
        break;
      default:
        // This should never happen
        throw new IllegalArgumentException(
                "Invalid http method " + okHttpRequest.method());
    }

    // Set url
    parseRequestBuilder.setUrl(okHttpRequest.url().toString());

    // Set Header
    for (Map.Entry<String, List<String>> entry : okHttpRequest.headers().toMultimap().entrySet()) {
      parseRequestBuilder.addHeader(entry.getKey(), entry.getValue().get(0));
    }

    // Set Body
    ParseOkHttpRequestBody okHttpBody = (ParseOkHttpRequestBody) okHttpRequest.body();
    if (okHttpBody != null) {
      parseRequestBuilder.setBody(okHttpBody.getParseHttpBody());
    }
    return parseRequestBuilder.build();
  }

  private static class ParseOkHttpRequestBody extends RequestBody {

    private ParseHttpBody parseBody;

    public ParseOkHttpRequestBody(ParseHttpBody parseBody) {
      this.parseBody = parseBody;
    }

    @Override
    public long contentLength() throws IOException {
      return parseBody.getContentLength();
    }

    @Override
    public MediaType contentType() {
      String contentType = parseBody.getContentType();
      return contentType == null ? null : MediaType.parse(parseBody.getContentType());
    }

    @Override
    public void writeTo(BufferedSink bufferedSink) throws IOException {
      parseBody.writeTo(bufferedSink.outputStream());
    }

    public ParseHttpBody getParseHttpBody() {
      return parseBody;
    }
  }
}

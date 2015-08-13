/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import android.net.SSLCertificateSocketFactory;
import android.net.SSLSessionCache;

import com.squareup.okhttp.Call;
import com.squareup.okhttp.Headers;
import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;
import com.squareup.okhttp.ResponseBody;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okio.BufferedSink;

/** package */ class ParseOkHttpClient extends ParseHttpClient<Request, Response> {

  private OkHttpClient okHttpClient;

  public ParseOkHttpClient(int socketOperationTimeout, SSLSessionCache sslSessionCache) {

    okHttpClient = new OkHttpClient();

    okHttpClient.setConnectTimeout(socketOperationTimeout, TimeUnit.MILLISECONDS);
    okHttpClient.setReadTimeout(socketOperationTimeout, TimeUnit.MILLISECONDS);

    // Don't handle redirects. We copy the setting from AndroidHttpClient.
    // For detail, check https://quip.com/Px8jAxnaun2r
    okHttpClient.setFollowRedirects(false);

    okHttpClient.setSslSocketFactory(SSLCertificateSocketFactory.getDefault(
        socketOperationTimeout, sslSessionCache));
  }

  @Override
  /* package */ ParseHttpResponse executeInternal(ParseHttpRequest parseRequest) throws IOException {
    Request okHttpRequest = getRequest(parseRequest);
    Call okHttpCall = okHttpClient.newCall(okHttpRequest);

    Response okHttpResponse = okHttpCall.execute();

    return getResponse(okHttpResponse);
  }

  @Override
  /* package */ ParseHttpResponse getResponse(Response okHttpResponse)
      throws IOException {
    // Status code
    int statusCode = okHttpResponse.code();

    // Content
    InputStream content = okHttpResponse.body().byteStream();

    // Total size
    int totalSize = (int)okHttpResponse.body().contentLength();

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
        .setReasonPhase(reasonPhrase)
        .setHeaders(headers)
        .setContentType(contentType)
        .build();
  }

  @Override
  /* package */ Request getRequest(ParseHttpRequest parseRequest) throws IOException {
    Request.Builder okHttpRequestBuilder = new Request.Builder();
    ParseRequest.Method method = parseRequest.getMethod();
    // Set method
    switch (method) {
      case GET:
        okHttpRequestBuilder.get();
        break;
      case DELETE:
        okHttpRequestBuilder.delete();
        break;
      case POST:
      case PUT:
        // Since we need to set body and method at the same time for POST and PUT, we will do it in
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
    CountingOkHttpRequestBody okHttpRequestBody = null;
    if(parseBody instanceof ParseByteArrayHttpBody) {
      okHttpRequestBody = new CountingOkHttpRequestBody(parseBody);
    }
    switch (method) {
      case PUT:
        okHttpRequestBuilder.put(okHttpRequestBody);
        break;
      case POST:
        okHttpRequestBuilder.post(okHttpRequestBody);
        break;
    }
    return okHttpRequestBuilder.build();
  }

  private static class CountingOkHttpRequestBody extends RequestBody {

    private ParseHttpBody parseBody;

    public CountingOkHttpRequestBody(ParseHttpBody parseBody) {
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
  }
}

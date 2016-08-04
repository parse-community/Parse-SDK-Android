/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import android.net.SSLSessionCache;

import com.parse.http.ParseHttpBody;
import com.parse.http.ParseHttpRequest;
import com.parse.http.ParseHttpResponse;
import com.parse.http.ParseNetworkInterceptor;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import bolts.Capture;
import okhttp3.Call;
import okhttp3.Headers;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSink;
import okio.BufferedSource;
import okio.Okio;

/** package */ class ParseOkHttpClient extends ParseHttpClient<Request, Response> {

  private final static String OKHTTP_GET = "GET";
  private final static String OKHTTP_POST = "POST";
  private final static String OKHTTP_PUT = "PUT";
  private final static String OKHTTP_DELETE = "DELETE";

  private OkHttpClient okHttpClient;

  public ParseOkHttpClient(int socketOperationTimeout, SSLSessionCache sslSessionCache) {

    OkHttpClient.Builder builder = new OkHttpClient.Builder();

    builder.connectTimeout(socketOperationTimeout, TimeUnit.MILLISECONDS);
    builder.readTimeout(socketOperationTimeout, TimeUnit.MILLISECONDS);

    // Don't handle redirects. We copy the setting from AndroidHttpClient.
    // For detail, check https://quip.com/Px8jAxnaun2r
    builder.followRedirects(false);

    okHttpClient = builder.build();
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
        .setReasonPhrase(reasonPhrase)
        .setHeaders(headers)
        .setContentType(contentType)
        .build();
  }

  @Override
  /* package */ Request getRequest(ParseHttpRequest parseRequest) throws IOException {
    Request.Builder okHttpRequestBuilder = new Request.Builder();
    ParseHttpRequest.Method method = parseRequest.getMethod();
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
    ParseOkHttpRequestBody okHttpRequestBody = null;
    if(parseBody instanceof ParseByteArrayHttpBody) {
      okHttpRequestBody = new ParseOkHttpRequestBody(parseBody);
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

  /**
   * For OKHttpClient, since it does not expose any interface for us to check the raw response
   * stream, we have to use OKHttp networkInterceptors. Instead of using our own interceptor list,
   * we use OKHttp inner interceptor list.
   * @param parseNetworkInterceptor
   */
  @Override
  /* package */ void addExternalInterceptor(final ParseNetworkInterceptor parseNetworkInterceptor) {
    OkHttpClient.Builder builder = okHttpClient.newBuilder();
    builder.networkInterceptors().add(new Interceptor() {
      @Override
      public Response intercept(final Chain okHttpChain) throws IOException {
        Request okHttpRequest = okHttpChain.request();
        // Transfer OkHttpRequest to ParseHttpRequest
        final ParseHttpRequest parseRequest = getParseHttpRequest(okHttpRequest);
        // Capture OkHttpResponse
        final Capture<Response> okHttpResponseCapture = new Capture<>();
        final ParseHttpResponse parseResponse =
            parseNetworkInterceptor.intercept(new ParseNetworkInterceptor.Chain() {
          @Override
          public ParseHttpRequest getRequest() {
            return parseRequest;
          }

          @Override
          public ParseHttpResponse proceed(ParseHttpRequest parseRequest) throws IOException {
            // Use OKHttpClient to send request
            Request okHttpRequest = ParseOkHttpClient.this.getRequest(parseRequest);
            Response okHttpResponse = okHttpChain.proceed(okHttpRequest);
            okHttpResponseCapture.set(okHttpResponse);
            return getResponse(okHttpResponse);
          }
        });
        final Response okHttpResponse = okHttpResponseCapture.get();
        // Ideally we should build newOkHttpResponse only based on parseResponse, however
        // ParseHttpResponse does not have all the info we need to build the newOkHttpResponse, so
        // we rely on the okHttpResponse to generate the builder and change the necessary info
        // inside
        Response.Builder newOkHttpResponseBuilder =  okHttpResponse.newBuilder();
        // Set status
        newOkHttpResponseBuilder
            .code(parseResponse.getStatusCode())
            .message(parseResponse.getReasonPhrase());
        // Set headers
        if (parseResponse.getAllHeaders() != null) {
          for (Map.Entry<String, String> entry : parseResponse.getAllHeaders().entrySet()) {
            newOkHttpResponseBuilder.header(entry.getKey(), entry.getValue());
          }
        }
        // Set body
        newOkHttpResponseBuilder.body(new ResponseBody() {
          @Override
          public MediaType contentType() {
            if (parseResponse.getContentType() == null) {
              return null;
            }
            return MediaType.parse(parseResponse.getContentType());
          }

          @Override
          public long contentLength() {
            return parseResponse.getTotalSize();
          }

          @Override
          public BufferedSource source() {
            // We need to use the proxy stream from interceptor to replace the origin network
            // stream, so when the stream is read by Parse, the network stream is proxyed in the
            // interceptor.
            if (parseResponse.getContent() == null) {
              return null;
            }
            return Okio.buffer(Okio.source(parseResponse.getContent()));
          }
        });

        return newOkHttpResponseBuilder.build();
      }
    });

    okHttpClient = builder.build();
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

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

import com.parse.http.ParseHttpBody;
import com.parse.http.ParseHttpRequest;
import com.parse.http.ParseHttpResponse;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;

/** package */ class ParseURLConnectionHttpClient extends ParseHttpClient<HttpURLConnection, HttpURLConnection> {

  private static final String ACCEPT_ENCODING_HEADER = "Accept-encoding";
  private static final String GZIP_ENCODING = "gzip";
  private static final String CONTENT_LENGTH_HEADER = "Content-Length";
  private static final String CONTENT_TYPE_HEADER = "Content-Type";

  private int socketOperationTimeout;

  public ParseURLConnectionHttpClient(int socketOperationTimeout, SSLSessionCache sslSessionCache) {
    this.socketOperationTimeout = socketOperationTimeout;
  }

  @Override
  /* package */ ParseHttpResponse executeInternal(ParseHttpRequest parseRequest) throws IOException {
    HttpURLConnection connection = getRequest(parseRequest);
    // Start network connection and write data to server if possible
    ParseHttpBody body = parseRequest.getBody();
    if (body != null) {
      OutputStream outputStream = connection.getOutputStream();
      body.writeTo(outputStream);
      outputStream.flush();
      outputStream.close();
    }

    return getResponse(connection);
  }

  @Override
  /* package */ HttpURLConnection getRequest(ParseHttpRequest parseRequest)
      throws IOException {
    HttpURLConnection connection;
    URL url = new URL(parseRequest.getUrl());

    connection = (HttpURLConnection)url.openConnection();

    connection.setRequestMethod(parseRequest.getMethod().toString());

    connection.setConnectTimeout(socketOperationTimeout);
    connection.setReadTimeout(socketOperationTimeout);
    connection.setDoInput(true);

    // Don't handle redirects. We copy the setting from AndroidHttpClient.
    // For detail, check https://quip.com/Px8jAxnaun2r
    connection.setInstanceFollowRedirects(false);

    // Set header
    for (Map.Entry<String, String> entry : parseRequest.getAllHeaders().entrySet()) {
      connection.setRequestProperty(entry.getKey(), entry.getValue());
    }

    // When URLConnection is powered by OkHttp, by adding this head, OkHttp will turn off its
    // transparent decompress which will expose the raw network stream to our interceptors.
    if (disableHttpLibraryAutoDecompress()) {
      connection.setRequestProperty(ACCEPT_ENCODING_HEADER, GZIP_ENCODING);
    }
    // Set body
    ParseHttpBody body = parseRequest.getBody();
    if (body != null) {
      // Content type and content length
      connection.setRequestProperty(CONTENT_LENGTH_HEADER, String.valueOf(body.getContentLength()));
      connection.setRequestProperty(CONTENT_TYPE_HEADER, body.getContentType());
      // We need to set this in order to make URLConnection not buffer our request body so that our
      // upload progress callback works.
      connection.setFixedLengthStreamingMode(body.getContentLength());
      connection.setDoOutput(true);
    }
    return connection;
  }

  @Override
  /* package */ ParseHttpResponse getResponse(HttpURLConnection connection)
      throws IOException {
    // Status code
    int statusCode = connection.getResponseCode();

    // Content
    InputStream content;
    if (statusCode < 400) {
      content = connection.getInputStream();
    } else {
      content = connection.getErrorStream();
    }

    // Total size
    int totalSize = connection.getContentLength();

    // Reason phrase
    String reasonPhrase = connection.getResponseMessage();

    // Headers
    Map<String, String> headers = new HashMap<>();
    for (Map.Entry<String, List<String>> entry : connection.getHeaderFields().entrySet()) {
      // The status code's key from header entry is always null(like null=HTTP/1.1 200 OK), since we
      // have already had statusCode in ParseHttpResponse, we just ignore this header entry.
      if (entry.getKey() != null && entry.getValue().size() > 0) {
        headers.put(entry.getKey(), entry.getValue() == null ? "" : entry.getValue().get(0));
      }
    }

    // Content type
    String contentType = connection.getContentType();

    return new ParseHttpResponse.Builder()
        .setStatusCode(statusCode)
        .setContent(content)
        .setTotalSize(totalSize)
        .setReasonPhrase(reasonPhrase)
        .setHeaders(headers)
        .setContentType(contentType)
        .build();
  }
}

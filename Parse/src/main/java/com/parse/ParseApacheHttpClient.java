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
import android.net.http.AndroidHttpClient;

import com.parse.http.ParseHttpBody;
import com.parse.http.ParseHttpRequest;
import com.parse.http.ParseHttpResponse;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.params.HttpClientParams;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.params.ConnManagerParams;
import org.apache.http.conn.params.ConnPerRouteBean;
import org.apache.http.conn.params.ConnRoutePNames;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * An implementation of ParseHttpClient using Apache httpclient library
 */
@SuppressWarnings("deprecation")
/** package */ class ParseApacheHttpClient extends ParseHttpClient<HttpUriRequest, HttpResponse> {

  private static final String CONTENT_ENCODING_HEADER = "Content-Encoding";

  private DefaultHttpClient apacheClient;

  public ParseApacheHttpClient(int socketOperationTimeout, SSLSessionCache sslSessionCache) {
    // Most of this is from AndroidHttpClient#newInstance() except [1] and [2]
    HttpParams params = new BasicHttpParams();

    // Turn off stale checking.  Our connections break all the time anyway,
    // and it's not worth it to pay the penalty of checking every time.
    HttpConnectionParams.setStaleCheckingEnabled(params, false);

    HttpConnectionParams.setConnectionTimeout(params, socketOperationTimeout);
    HttpConnectionParams.setSoTimeout(params, socketOperationTimeout);
    HttpConnectionParams.setSocketBufferSize(params, 8192);

    // Don't handle redirects. We copy the setting from AndroidHttpClient.
    // For detail, check https://quip.com/Px8jAxnaun2r
    HttpClientParams.setRedirecting(params, false);

    // Register standard protocols.
    SchemeRegistry schemeRegistry = new SchemeRegistry();
    schemeRegistry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
    schemeRegistry.register(new Scheme("https", SSLCertificateSocketFactory.getHttpSocketFactory(
        socketOperationTimeout, sslSessionCache), 443));

    // [1] AndroidHttpClient defaults to 2 connections per route. Not fun. AND you can't set these
    // properties after AndroidHttpClient#newInstance(context)
    String maxConnectionsStr = System.getProperty("http.maxConnections");
    if (maxConnectionsStr != null) {
      int maxConnections = Integer.parseInt(maxConnectionsStr);
      ConnManagerParams.setMaxConnectionsPerRoute(params, new ConnPerRouteBean(maxConnections));
      ConnManagerParams.setMaxTotalConnections(params, maxConnections);
    }

    // [2] Originally from ParseCommand, check proxy
    String host = System.getProperty("http.proxyHost");
    String portString = System.getProperty("http.proxyPort");
    if (host != null && host.length() != 0 && portString != null && portString.length() != 0) {
      int port = Integer.parseInt(portString);
      HttpHost proxy = new HttpHost(host, port, "http");
      params.setParameter(ConnRoutePNames.DEFAULT_PROXY, proxy);
    }

    ClientConnectionManager manager = new ThreadSafeClientConnManager(params, schemeRegistry);
    apacheClient = new DefaultHttpClient(manager, params);

    // Disable retry logic by ApacheHttpClient. When we leave the app idle for 3 - 5 min, the next
    // request will always fail with NoHttpResponseException: The target server failed to respond,
    // in this situation, the Apache httpClient will try to retry the request,
    // however, since we use InputStreamEntity which is non-repeatable, we will see the
    // NonRepeatableRequestException: Cannot retry request with a non-repeatable request entity.
    // We disable the retry logic by ApacheHttpClient to expose the real issue
    apacheClient.setHttpRequestRetryHandler(new DefaultHttpRequestRetryHandler(0, false));
  }

  @Override
  /* package */ ParseHttpResponse executeInternal(ParseHttpRequest parseRequest) throws IOException {
    HttpUriRequest apacheRequest = getRequest(parseRequest);

    HttpResponse apacheResponse = apacheClient.execute(apacheRequest);

    return getResponse(apacheResponse);
  }

  @Override
  /* package */ ParseHttpResponse getResponse(HttpResponse apacheResponse)
      throws IOException {
    if (apacheResponse == null) {
      throw new IllegalArgumentException(
          "HttpResponse passed to getResponse should not be null."
      );
    }

    // Status code
    int statusCode = apacheResponse.getStatusLine().getStatusCode();

    // Content
    InputStream content = disableHttpLibraryAutoDecompress() ?
        apacheResponse.getEntity().getContent() :
        AndroidHttpClient.getUngzippedContent(apacheResponse.getEntity());

    // Total size
    int totalSize = -1;
    Header[] contentLengthHeader = apacheResponse.getHeaders("Content-Length");
    // Some encodings, such as chunked encoding, forbid the
    // content-length header.
    if (contentLengthHeader.length > 0) {
      totalSize = Integer.parseInt(contentLengthHeader[0].getValue());
    }

    // Reason phrase
    String reasonPhrase = apacheResponse.getStatusLine().getReasonPhrase();

    // Headers
    Map<String, String> headers = new HashMap<>();
    for (Header header : apacheResponse.getAllHeaders()) {
      headers.put(header.getName(), header.getValue());
    }
    // If we auto unzip the response stream, we should remove the content-encoding header
    if (!disableHttpLibraryAutoDecompress()) {
      headers.remove(CONTENT_ENCODING_HEADER);
    }

    // Content type
    String contentType = null;
    HttpEntity entity = apacheResponse.getEntity();
    if (entity != null && entity.getContentType() != null) {
      contentType = entity.getContentType().getValue();
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
  /* package */ HttpUriRequest getRequest(ParseHttpRequest parseRequest)
      throws IOException {
    if (parseRequest == null) {
      throw new IllegalArgumentException(
          "ParseHttpRequest passed to getApacheRequest should not be null."
      );
    }

    HttpUriRequest apacheRequest;
    ParseHttpRequest.Method method = parseRequest.getMethod();
    String url = parseRequest.getUrl();
    switch (method) {
      case GET:
        apacheRequest = new HttpGet(url);
        break;
      case DELETE:
        apacheRequest = new HttpDelete(url);
        break;
      case POST:
        apacheRequest = new HttpPost(url);
        break;
      case PUT:
        apacheRequest = new HttpPut(url);
        break;
      default:
        // This case will never be reached since we have already handled this case in
        // ParseRequest.newRequest().
        throw new IllegalStateException("Unsupported http method " + method.toString());
    }

    // Set header
    for (Map.Entry<String, String> entry : parseRequest.getAllHeaders().entrySet()) {
      apacheRequest.setHeader(entry.getKey(), entry.getValue());
    }
    AndroidHttpClient.modifyRequestToAcceptGzipResponse(apacheRequest);

    // Set entity
    ParseHttpBody body = parseRequest.getBody();
    switch (method) {
      case POST:
        ((HttpPost) apacheRequest).setEntity(new ParseApacheHttpEntity(body));
        break;
      case PUT:
        ((HttpPut) apacheRequest).setEntity(new ParseApacheHttpEntity(body));
        break;
      default:
        break;
    }
    return apacheRequest;
  }

  /**
   * An wrapper of Apache InputStreamEntity. It takes a ParseHttpBody
   * and transfer it to a HttpEntity
   */
  private static class ParseApacheHttpEntity extends InputStreamEntity {
    private ParseHttpBody parseBody;

    public ParseApacheHttpEntity(ParseHttpBody parseBody) throws IOException {
      super(parseBody.getContent(), parseBody.getContentLength());
      super.setContentType(parseBody.getContentType());
      this.parseBody = parseBody;
    }

    @Override
    public void writeTo(OutputStream out) throws IOException {
      parseBody.writeTo(out);
    }
  }
}

/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * The base class of a http response we receive from parse server. It can be implemented by
 * different http library such as Apache http, Android URLConnection, Square OKHttp and so on.
 */
/** package */ class ParseHttpResponse {

  /* package */ static abstract class Init<T extends Init<T>> {
    private int statusCode;
    private InputStream content;
    private long totalSize;
    private String reasonPhrase;
    private Map<String, String> headers;
    private String contentType;

    /* package */ abstract T self();

    public Init() {
      this.headers = new HashMap<>();
    }

    public T setStatusCode(int statusCode) {
      this.statusCode = statusCode;
      return self();
    }

    public T setContent(InputStream content) {
      this.content = content;
      return self();
    }

    public T setTotalSize(long totalSize) {
      this.totalSize = totalSize;
      return self();
    }

    public T setReasonPhrase(String reasonPhrase) {
      this.reasonPhrase = reasonPhrase;
      return self();
    }

    public T setHeaders(Map<String, String> headers) {
      this.headers = new HashMap<>(headers);
      return self();
    }

    public T addHeaders(Map<String, String> headers) {
      this.headers.putAll(headers);
      return self();
    }

    public T addHeader(String key, String value) {
      this.headers.put(key, value);
      return self();
    }

    public T setContentType(String contentType) {
      this.contentType = contentType;
      return self();
    }
  }

  public static class Builder extends Init<Builder> {

    @Override
    /* package */ Builder self() {
      return this;
    }

    /* package */ Builder() {
      super();
    }

    /* package */ Builder(ParseHttpResponse response) {
      super();
      this.setStatusCode(response.getStatusCode());
      this.setContent(response.getContent());
      this.setTotalSize(response.getTotalSize());
      this.setContentType(response.getContentType());
      this.setHeaders(response.getAllHeaders());
      this.setReasonPhrase(response.getReasonPhrase());
    }

    public ParseHttpResponse build() {
      return new ParseHttpResponse(this);
    }
  }

  /* package */ final int statusCode;
  /* package */ final InputStream content;
  /* package */ final long totalSize;
  /* package */ final String reasonPhrase;
  /* package */ final Map<String, String> headers;
  /* package */ final String contentType;

  /* package */ ParseHttpResponse(Init<?> builder) {
    this.statusCode = builder.statusCode;
    this.content = builder.content;
    this.totalSize = builder.totalSize;
    this.reasonPhrase = builder.reasonPhrase;
    this.headers = Collections.unmodifiableMap(new HashMap<>(builder.headers));
    this.contentType = builder.contentType;
  }

  public Builder newBuilder() {
    return new Builder(this);
  }

  public int getStatusCode() {
    return statusCode;
  }

  public InputStream getContent() {
    return content;
  }

  public long getTotalSize() {
    return totalSize;
  }

  public String getReasonPhrase() {
    return reasonPhrase;
  }

  public String getContentType() {
    return contentType;
  }

  public String getHeader(String name) {
    return headers == null ? null : headers.get(name);
  }

  public Map<String, String> getAllHeaders() {
    return headers;
  }
}

/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/** package */ class ParseHttpRequest {
  private final String url;
  private final ParseRequest.Method method;
  private final Map<String, String> headers;
  private final ParseHttpBody body;

  protected ParseHttpRequest(Builder builder) {
    this.url = builder.url;
    this.method = builder.method;
    this.headers = Collections.unmodifiableMap(new HashMap<>(builder.headers));
    this.body = builder.body;
  }

  public String getUrl() {
    return url;
  }

  public ParseRequest.Method getMethod() {
    return method;
  }

  public Map<String, String> getAllHeaders() {
    return headers;
  }

  public String getHeader(String name) {
    return headers.get(name);
  }

  public ParseHttpBody getBody() {
    return body;
  }

  public static class Builder {
    protected String url;
    protected ParseRequest.Method method;
    protected Map<String, String> headers;
    protected ParseHttpBody body;

    public Builder() {
      this.headers = new HashMap<>();
    }

    public Builder(ParseHttpRequest request) {
      this.url = request.url;
      this.method = request.method;
      this.headers = new HashMap<>(request.headers);
      this.body = request.body;
    }

    public Builder setUrl(String url) {
      this.url = url;
      return this;
    }

    public Builder setMethod(ParseRequest.Method method) {
      this.method = method;
      return this;
    }

    public Builder setBody(ParseHttpBody body) {
      this.body = body;
      return this;
    }

    public Builder addHeader(String name, String value) {
      headers.put(name, value);
      return this;
    }

    public Builder setHeaders(Map<String, String> headers) {
      this.headers = headers;
      return this;
    }

    public ParseHttpRequest build() {
      return new ParseHttpRequest(this);
    }
  }
}

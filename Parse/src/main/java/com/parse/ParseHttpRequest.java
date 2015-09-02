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
import java.util.Map;

/** package */ class ParseHttpRequest {

  public enum Method {
    GET, POST, PUT, DELETE;

    public static Method fromString(String string) {
      Method method;
      switch (string) {
        case "GET":
          method = GET;
          break;
        case "POST":
          method = POST;
          break;
        case "PUT":
          method = PUT;
          break;
        case "DELETE":
          method = DELETE;
          break;
        default:
          throw new IllegalArgumentException("Invalid http method: <" + string + ">");
      }
      return method;
    }

    @Override
    public String toString() {
      String string;
      switch (this) {
        case GET:
          string = "GET";
          break;
        case POST:
          string = "POST";
          break;
        case PUT:
          string = "PUT";
          break;
        case DELETE:
          string = "DELETE";
          break;
        default:
          throw new IllegalArgumentException("Invalid http method: <" + this+ ">");
      }
      return string;
    }
  }

  private final String url;
  private final ParseHttpRequest.Method method;
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

  public ParseHttpRequest.Method getMethod() {
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
    protected ParseHttpRequest.Method method;
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

    public Builder setMethod(ParseHttpRequest.Method method) {
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

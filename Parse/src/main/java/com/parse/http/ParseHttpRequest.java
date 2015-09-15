/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse.http;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * The http request we send to parse server. Instances of this class are not immutable. The
 * request body may be consumed only once. The other fields are immutable.
 */
public final class ParseHttpRequest {

  /**
   * The {@code ParseHttpRequest} method type.
   */
  public enum Method {

    GET, POST, PUT, DELETE;

    /**
     * Creates a {@code Method} from the given string. Valid stings are {@code GET}, {@code POST},
     * {@code PUT} and {@code DELETE}.
     *
     * @param string
     *          The string value of this {@code Method}.
     * @return A {@code Method} based on the given string.
     */
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

    /**
     * Returns a string value of this {@code Method}.
     * @return The string value of this {@code Method}.
     */
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

  /**
   * Builder of {@code ParseHttpRequest}.
   */
  public static final class Builder {

    private String url;
    private Method method;
    private Map<String, String> headers;
    private ParseHttpBody body;

    /**
     * Creates an empty {@code Builder}.
     */
    public Builder() {
      this.headers = new HashMap<>();
    }

    /**
     * Creates a new {@code Builder} based on the given {@code ParseHttpRequest}.
     *
     * @param request
     *          The {@code ParseHttpRequest} where the {@code Builder}'s values come from.
     */
    public Builder(ParseHttpRequest request) {
      this.url = request.url;
      this.method = request.method;
      this.headers = new HashMap<>(request.headers);
      this.body = request.body;
    }

    /**
     * Sets the url of this {@code Builder}.
     *
     * @param url
     *          The url of this {@code Builder}.
     * @return This {@code Builder}.
     */
    public Builder setUrl(String url) {
      this.url = url;
      return this;
    }

    /**
     * Sets the {@link com.parse.http.ParseHttpRequest.Method} of this {@code Builder}.
     *
     * @param method
     *          The {@link com.parse.http.ParseHttpRequest.Method} of this {@code Builder}.
     * @return This {@code Builder}.
     */
    public Builder setMethod(ParseHttpRequest.Method method) {
      this.method = method;
      return this;
    }

    /**
     * Sets the {@link ParseHttpBody} of this {@code Builder}.
     *
     * @param body
     *          The {@link ParseHttpBody} of this {@code Builder}.
     * @return This {@code Builder}.
     */
    public Builder setBody(ParseHttpBody body) {
      this.body = body;
      return this;
    }

    /**
     * Adds a header to this {@code Builder}.
     *
     * @param name
     *          The name of the header.
     * @param value
     *          The value of the header.
     * @return This {@code Builder}.
     */
    public Builder addHeader(String name, String value) {
      headers.put(name, value);
      return this;
    }

    /**
     * Adds headers to this {@code Builder}.
     *
     * @param headers
     *          The headers that need to be added.
     * @return This {@code Builder}.
     */
    public Builder addHeaders(Map<String, String> headers) {
      this.headers.putAll(headers);
      return this;
    }

    /**
     * Sets headers of this {@code Builder}. All existing headers will be cleared.
     *
     * @param headers
     *          The headers of this {@code Builder}.
     * @return This {@code Builder}.
     */
    public Builder setHeaders(Map<String, String> headers) {
      this.headers = new HashMap<>(headers);
      return this;
    }

    /**
     * Builds a {@link ParseHttpRequest} based on this {@code Builder}.
     *
     * @return A {@link ParseHttpRequest} built on this {@code Builder}.
     */
    public ParseHttpRequest build() {
      return new ParseHttpRequest(this);
    }
  }

  private final String url;
  private final Method method;
  private final Map<String, String> headers;
  private final ParseHttpBody body;

  private ParseHttpRequest(Builder builder) {
    this.url = builder.url;
    this.method = builder.method;
    this.headers = Collections.unmodifiableMap(new HashMap<>(builder.headers));
    this.body = builder.body;
  }

  /**
   * Gets the url of this {@code ParseHttpRequest}.
   *
   * @return The url of this {@code ParseHttpRequest}.
   */
  public String getUrl() {
    return url;
  }

  /**
   * Gets the {@code Method} of this {@code ParseHttpRequest}.
   *
   * @return The {@code Method} of this {@code ParseHttpRequest}.
   */
  public Method getMethod() {
    return method;
  }

  /**
   * Gets all headers from this {@code ParseHttpRequest}.
   *
   * @return The headers of this {@code ParseHttpRequest}.
   */
  public Map<String, String> getAllHeaders() {
    return headers;
  }

  /**
   * Retrieves the header value from this {@code ParseHttpRequest} by the given header name.
   *
   * @param name
   *          The name of the header.
   * @return The value of the header.
   */
  public String getHeader(String name) {
    return headers.get(name);
  }

  /**
   * Gets http body of this {@code ParseHttpRequest}.
   *
   * @return The http body of this {@code ParseHttpRequest}.
   */
  public ParseHttpBody getBody() {
    return body;
  }
}

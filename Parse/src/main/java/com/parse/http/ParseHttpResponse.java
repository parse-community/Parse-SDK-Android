/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse.http;

import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * The http response we receive from parse server. Instances of this class are not immutable. The
 * response body may be consumed only once. The other fields are immutable.
 */
public final class ParseHttpResponse {

  /**
   * Builder for {@code ParseHttpResponse}.
   */
  public static final class Builder {

    private int statusCode;
    private InputStream content;
    private long totalSize;
    private String reasonPhrase;
    private Map<String, String> headers;
    private String contentType;

    /**
     * Creates an empty {@code Builder}.
     */
    public Builder() {
      this.totalSize = -1;
      this.headers = new HashMap<>();
    }

    /**
     * Makes a new {@code Builder} based on the given {@code ParseHttpResponse}.
     *
     * @param response
     *          The {@code ParseHttpResponse} where the {@code Builder}'s values come from.
     */
    public Builder(ParseHttpResponse response) {
      super();
      this.setStatusCode(response.getStatusCode());
      this.setContent(response.getContent());
      this.setTotalSize(response.getTotalSize());
      this.setContentType(response.getContentType());
      this.setHeaders(response.getAllHeaders());
      this.setReasonPhrase(response.getReasonPhrase());
    }

    /**
     * Sets the status code of this {@code Builder}.
     *
     * @param statusCode
     *          The status code of this {@code Builder}.
     * @return This {@code Builder}.
     */
    public Builder setStatusCode(int statusCode) {
      this.statusCode = statusCode;
      return this;
    }

    /**
     * Sets the content of this {@code Builder}.
     *
     * @param content
     *          The content of this {@code Builder}.
     * @return This {@code Builder}.
     */
    public Builder setContent(InputStream content) {
      this.content = content;
      return this;
    }

    /**
     * Sets the total size of this {@code Builder}.
     *
     * @param totalSize
     *          The total size of this {@code Builder}.
     * @return This {@code Builder}.
     */
    public Builder setTotalSize(long totalSize) {
      this.totalSize = totalSize;
      return this;
    }

    /**
     * Sets the reason phrase of this {@code Builder}.
     *
     * @param reasonPhrase
     *          The reason phrase of this {@code Builder}.
     * @return This {@code Builder}.
     */
    public Builder setReasonPhrase(String reasonPhrase) {
      this.reasonPhrase = reasonPhrase;
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
     * Sets the content type of this {@code Builder}.
     *
     * @param contentType
     *          The {@code Content-Type} of this {@code Builder}.
     * @return This {@code Builder}.
     */
    public Builder setContentType(String contentType) {
      this.contentType = contentType;
      return this;
    }

    /**
     * Builds a {@link ParseHttpResponse} by this {@code Builder}.
     *
     * @return A {@link ParseHttpResponse} built on this {@code Builder}.
     */
    public ParseHttpResponse build() {
      return new ParseHttpResponse(this);
    }
  }

  private final int statusCode;
  private final InputStream content;
  private final long totalSize;
  private final String reasonPhrase;
  private final Map<String, String> headers;
  private final String contentType;

  private ParseHttpResponse(Builder builder) {
    this.statusCode = builder.statusCode;
    this.content = builder.content;
    this.totalSize = builder.totalSize;
    this.reasonPhrase = builder.reasonPhrase;
    this.headers = Collections.unmodifiableMap(new HashMap<>(builder.headers));
    this.contentType = builder.contentType;
  }

  /**
   * Gets the status code of this {@code ParseHttpResponse}.
   *
   * @return The status code of this {@code ParseHttpResponse}.
   */
  public int getStatusCode() {
    return statusCode;
  }

  /**
   * Returns the content of the {@code ParseHttpResponse}'s body. The content can only
   * be read once and can't be reset.
   *
   * @return The content of the {@code ParseHttpResponse}'s body.
   */
  public InputStream getContent() {
    return content;
  }

  /**
   * Returns the size of the {@code ParseHttpResponse}'s body. {@code -1} if the size of the
   * {@code ParseHttpResponse}'s body is unknown.
   *
   * @return The size of the {@code ParseHttpResponse}'s body.
   */
  public long getTotalSize() {
    return totalSize;
  }

  /**
   * Gets the reason phrase of this {@code ParseHttpResponse}.
   *
   * @return The reason phrase of this {@code ParseHttpResponse}.
   */
  public String getReasonPhrase() {
    return reasonPhrase;
  }

  /**
   * Gets the {@code Content-Type} of this {@code ParseHttpResponse}.
   *
   * @return The {@code Content-Type} of this {@code ParseHttpResponse}.
   */
  public String getContentType() {
    return contentType;
  }

  /**
   * Retrieves the header value from this {@code ParseHttpResponse} by the given header name.
   *
   * @param name
   *          The name of the header.
   * @return The value of the header.
   */
  public String getHeader(String name) {
    return headers.get(name);
  }

  /**
   * Gets all headers from this {@code ParseHttpResponse}.
   *
   * @return The headers of this {@code ParseHttpResponse}.
   */
  public Map<String, String> getAllHeaders() {
    return headers;
  }
}

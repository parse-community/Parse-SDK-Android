/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

/**
 * REST network command for creating & uploading {@link ParseFile}s.
 */
/** package */ class ParseRESTFileCommand extends ParseRESTCommand {

  public static class Builder extends Init<Builder> {

    private byte[] data = null;
    private String contentType = null;

    public Builder() {
      // We only ever use ParseRESTFileCommand for file uploads, so default to POST.
      method(ParseHttpRequest.Method.POST);
    }

    public Builder fileName(String fileName) {
      return httpPath(String.format("files/%s", fileName));
    }

    public Builder data(byte[] data) {
      this.data = data;
      return this;
    }

    public Builder contentType(String contentType) {
      this.contentType = contentType;
      return this;
    }

    @Override
    /* package */ Builder self() {
      return this;
    }

    public ParseRESTFileCommand build() {
      return new ParseRESTFileCommand(this);
    }
  }

  private final byte[] data;
  private final String contentType;

  public ParseRESTFileCommand(Builder builder) {
    super(builder);
    this.data = builder.data;
    this.contentType = builder.contentType;
  }

  @Override
  protected ParseHttpBody newBody(final ProgressCallback progressCallback) {
    if (progressCallback == null) {
      return new ParseByteArrayHttpBody(data, contentType);
    }
    return new ParseCountingByteArrayHttpBody(data, contentType, progressCallback);
  }
}

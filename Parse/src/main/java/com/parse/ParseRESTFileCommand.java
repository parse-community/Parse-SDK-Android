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

import com.parse.http.ParseHttpBody;
import com.parse.http.ParseHttpRequest;

import java.io.File;

/** package */ class ParseRESTFileCommand extends ParseRESTCommand {

  public static class Builder extends Init<Builder> {

    private byte[] data = null;
    private String contentType = null;
    private File file;

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

    public Builder file(File file) {
      this.file = file;
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
  private final File file;

  public ParseRESTFileCommand(Builder builder) {
    super(builder);
    if (builder.file != null && builder.data != null) {
      throw new IllegalArgumentException("File and data can not be set at the same time");
    }
    this.data = builder.data;
    this.contentType = builder.contentType;
    this.file = builder.file;
  }

  @Override
  protected ParseHttpBody newBody(final ProgressCallback progressCallback) {
    // TODO(mengyan): Delete ParseByteArrayHttpBody when we change input byte array to staged file
    // in ParseFileController
    if (progressCallback == null) {
      return data != null ?
          new ParseByteArrayHttpBody(data, contentType) : new ParseFileHttpBody(file, contentType);
    }
    return data != null ?
        new ParseCountingByteArrayHttpBody(data, contentType, progressCallback) :
        new ParseCountingFileHttpBody(file, contentType, progressCallback);
  }
}

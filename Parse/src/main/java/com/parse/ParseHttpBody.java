/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * The base interface of a http body.It can be implemented by different http library such as
 * Apache http, Android URLConnection, Square OKHttp and so on.
 */
/** package */ abstract class ParseHttpBody {
  protected final String contentType;
  protected final int contentLength;

  public abstract InputStream getContent();
  public abstract void writeTo(OutputStream out) throws IOException;

  public ParseHttpBody(String contentType, int contentLength) {
    this.contentType = contentType;
    this.contentLength = contentLength;
  }

  public int getContentLength() {
    return contentLength;
  }

  public String getContentType() {
    return contentType;
  }
}

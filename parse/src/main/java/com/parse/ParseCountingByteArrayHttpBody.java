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
import java.io.OutputStream;
import java.util.concurrent.Callable;

import bolts.Task;

import static java.lang.Math.min;

/** package */ class ParseCountingByteArrayHttpBody extends ParseByteArrayHttpBody {
  private static final int DEFAULT_CHUNK_SIZE = 4096;
  private final ProgressCallback progressCallback;

  public ParseCountingByteArrayHttpBody(byte[] content, String contentType,
      final ProgressCallback progressCallback) {
    super(content, contentType);
    this.progressCallback = progressCallback;
  }

  @Override
  public void writeTo(OutputStream out) throws IOException {
    if (out == null) {
      throw new IllegalArgumentException("Output stream may not be null");
    }

    int position = 0;
    int totalLength = content.length;
    while (position < totalLength) {
      int length = min(totalLength - position, DEFAULT_CHUNK_SIZE);

      out.write(content, position, length);
      out.flush();

      if (progressCallback != null) {
        position += length;

        int progress = 100 * position / totalLength;
        progressCallback.done(progress);
      }
    }
  }
}

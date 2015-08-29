/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import bolts.Task;

/**
 * Request returns a byte array of the response and provides a callback the progress of the data
 * read from the network.
 */
/** package */ class ParseAWSRequest extends ParseRequest<byte[]> {

  public ParseAWSRequest(Method method, String url) {
    super(method, url);
  }

  @Override
  protected Task<byte[]> onResponseAsync(ParseHttpResponse response,
      final ProgressCallback downloadProgressCallback) {
    int statusCode = response.getStatusCode();
    if (statusCode >= 200 && statusCode < 300 || statusCode == 304) {
      // OK
    } else {
      String action = method == Method.GET ? "Download from" : "Upload to";
      return Task.forError(new ParseException(ParseException.CONNECTION_FAILED, String.format(
        "%s S3 failed. %s", action, response.getReasonPhrase())));
    }

    if (method != Method.GET) {
      return null;
    }

    long totalSize = response.getTotalSize();
    int downloadedSize = 0;
    InputStream responseStream = null;
    try {
      responseStream = response.getContent();
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();

      int nRead;
      byte[] data = new byte[32 << 10]; // 32KB

      while ((nRead = responseStream.read(data, 0, data.length)) != -1) {
        buffer.write(data, 0, nRead);
        downloadedSize += nRead;
        if (downloadProgressCallback != null && totalSize != -1) {
          int progressToReport = Math.round((float) downloadedSize / (float) totalSize * 100.0f);
          downloadProgressCallback.done(progressToReport);
        }
      }
      return Task.forResult(buffer.toByteArray());
    } catch (IOException e) {
      return Task.forError(e);
    } finally {
      ParseIOUtils.closeQuietly(responseStream);
    }
  }
}

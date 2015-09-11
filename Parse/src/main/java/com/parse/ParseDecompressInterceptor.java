/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */

package com.parse;

import com.parse.http.ParseHttpRequest;
import com.parse.http.ParseHttpResponse;
import com.parse.http.ParseNetworkInterceptor;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/** package */ class ParseDecompressInterceptor implements ParseNetworkInterceptor {

  private static final String CONTENT_ENCODING_HEADER = "Content-Encoding";
  private static final String CONTENT_LENGTH_HEADER = "Content-Length";
  private static final String GZIP_ENCODING = "gzip";

  @Override
  public ParseHttpResponse intercept(Chain chain) throws IOException {
    ParseHttpRequest request = chain.getRequest();
    ParseHttpResponse response = chain.proceed(request);
    // If the response is gziped, we need to decompress the stream and remove the gzip header.
    if (GZIP_ENCODING.equalsIgnoreCase(response.getHeader(CONTENT_ENCODING_HEADER))) {
      Map<String, String > newHeaders = new HashMap<>(response.getAllHeaders());
      newHeaders.remove(CONTENT_ENCODING_HEADER);
      // Since before we decompress the stream, we can not know the actual length of the stream.
      // In this situation, we follow the OkHttp library, set the content-length of the response
      // to -1
      newHeaders.put(CONTENT_LENGTH_HEADER, "-1");
      // TODO(mengyan): Add builder constructor based on an existing ParseHttpResponse
      response = new ParseHttpResponse.Builder(response)
          .setTotalSize(-1)
          .setHeaders(newHeaders)
          .setContent(new GZIPInputStream(response.getContent()))
          .build();
    }
    return response;
  }
}


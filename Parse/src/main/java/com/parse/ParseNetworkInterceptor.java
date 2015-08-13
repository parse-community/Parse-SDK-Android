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

// TODO(mengyan): Add java doc and make it public before we launch it
/** package */ interface ParseNetworkInterceptor {

  ParseHttpResponse intercept(Chain chain) throws IOException;

  interface Chain {
    ParseHttpRequest getRequest();
    ParseHttpResponse proceed(ParseHttpRequest request) throws IOException;
  }
}

/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse.http;

import java.io.IOException;

/**
 * {@code ParseNetworkInterceptor} is used to observe requests going out and the corresponding
 * responses coming back in.
 */
public interface ParseNetworkInterceptor {

  /**
   * Intercepts a {@link ParseHttpRequest} with the help of
   * {@link com.parse.http.ParseNetworkInterceptor.Chain} and returns the intercepted
   * {@link ParseHttpResponse}.
   *
   * @param chain
   *          The helper chain we use to get the request, proceed the request and receive the
   *          response.
   * @return The intercepted response.
   * @throws IOException
   */
  ParseHttpResponse intercept(Chain chain) throws IOException;

  /**
   * {@code Chain} is used to chain the interceptors. It can get the request from the previous
   * interceptor, proceed the request to the next interceptor and get the response from the next
   * interceptor. In most of the cases, you don't need to implement this interface.
   */
  interface Chain {

    /**
     * Gets the {@link ParseHttpRequest} from this chain.
     *
     * @return The {@link ParseHttpRequest} of this chain.
     */
    ParseHttpRequest getRequest();

    /**
     * Proceeds the intercepted {@link ParseHttpRequest} in this chain to next
     * {@code ParseNetworkInterceptor} or network and gets the {@link ParseHttpResponse}.
     *
     * @param request
     *          The intercepted {@link ParseHttpRequest}.
     * @return The {@link ParseHttpResponse} from next {@code ParseNetworkInterceptor} or network.
     * @throws IOException
     */
    ParseHttpResponse proceed(ParseHttpRequest request) throws IOException;
  }
}

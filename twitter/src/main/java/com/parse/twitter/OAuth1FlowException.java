/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse.twitter;

/**
 * OAuth Flow exception
 */
public class OAuth1FlowException extends Exception {
    private static final long serialVersionUID = 4272662026279290823L;
    private final int errorCode;
    private final String description;
    private final String failingUrl;

    public OAuth1FlowException(int errorCode, String description, String failingUrl) {
        super(String.format("OAuth Flow Error %d: Url: %s Description: %s", errorCode, failingUrl,
                description));
        this.errorCode = errorCode;
        this.description = description;
        this.failingUrl = failingUrl;
    }

    public int getErrorCode() {
        return errorCode;
    }

    public String getDescription() {
        return description;
    }

    public String getFailingUrl() {
        return failingUrl;
    }
}

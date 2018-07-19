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
import java.io.InputStream;
import java.io.OutputStream;

/**
 * The base interface of a http body. It can be implemented by different http libraries such as
 * Apache http, Android URLConnection, Square OKHttp and so on.
 */
public abstract class ParseHttpBody {

    private final String contentType;
    private final long contentLength;

    /**
     * Creates an {@code ParseHttpBody} with given {@code Content-Type} and {@code Content-Length}.
     *
     * @param contentType   The {@code Content-Type} of the {@code ParseHttpBody}.
     * @param contentLength The {@code Content-Length} of the {@code ParseHttpBody}.
     */
    public ParseHttpBody(String contentType, long contentLength) {
        this.contentType = contentType;
        this.contentLength = contentLength;
    }

    /**
     * Returns the content of this body.
     *
     * @return The content of this body.
     * @throws IOException Throws an exception if the content of this body is inaccessible.
     */
    public abstract InputStream getContent() throws IOException;

    /**
     * Writes the content of this request to {@code out}.
     *
     * @param out The outputStream the content of this body needs to be written to.
     * @throws IOException Throws an exception if the content of this body can not be written to {@code out}.
     */
    public abstract void writeTo(OutputStream out) throws IOException;

    /**
     * Returns the number of bytes which will be written to {@code out} when {@link #writeTo} is
     * called, or {@code -1} if that count is unknown.
     *
     * @return The Content-Length of this body.
     */
    public long getContentLength() {
        return contentLength;
    }

    /**
     * Returns the {@code Content-Type} of this body.
     *
     * @return The {@code Content-Type} of this body.
     */
    public String getContentType() {
        return contentType;
    }
}

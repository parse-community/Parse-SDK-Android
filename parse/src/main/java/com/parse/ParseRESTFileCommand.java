/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import android.net.Uri;
import com.parse.http.ParseHttpBody;
import com.parse.http.ParseHttpRequest;
import java.io.File;

/** REST network command for creating & uploading {@link ParseFile}s. */
class ParseRESTFileCommand extends ParseRESTCommand {

    private final byte[] data;
    private final String contentType;
    private final File file;
    private final Uri uri;

    public ParseRESTFileCommand(Builder builder) {
        super(builder);
        if (builder.file != null && builder.data != null) {
            throw new IllegalArgumentException("File and data can not be set at the same time");
        }
        if (builder.uri != null && builder.data != null) {
            throw new IllegalArgumentException("URI and data can not be set at the same time");
        }
        if (builder.file != null && builder.uri != null) {
            throw new IllegalArgumentException("File and URI can not be set at the same time");
        }
        this.data = builder.data;
        this.contentType = builder.contentType;
        this.file = builder.file;
        this.uri = builder.uri;
    }

    @Override
    protected ParseHttpBody newBody(final ProgressCallback progressCallback) {
        // TODO(mengyan): Delete ParseByteArrayHttpBody when we change input byte array to staged
        // file
        // in ParseFileController
        if (progressCallback == null) {
            if (data != null) {
                return new ParseByteArrayHttpBody(data, contentType);
            } else if (uri != null) {
                return new ParseUriHttpBody(uri, contentType);
            } else {
                return new ParseFileHttpBody(file, contentType);
            }
        }
        if (data != null) {
            return new ParseCountingByteArrayHttpBody(data, contentType, progressCallback);
        } else if (uri != null) {
            return new ParseCountingUriHttpBody(uri, contentType, progressCallback);
        } else {
            return new ParseCountingFileHttpBody(file, contentType, progressCallback);
        }
    }

    public static class Builder extends Init<Builder> {

        private byte[] data = null;
        private String contentType = null;
        private File file;
        private Uri uri;

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

        public Builder uri(Uri uri) {
            this.uri = uri;
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
}

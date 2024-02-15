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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

class ParseCountingUriHttpBody extends ParseUriHttpBody {

    private static final int DEFAULT_CHUNK_SIZE = 4096;
    private static final int EOF = -1;

    private final ProgressCallback progressCallback;

    public ParseCountingUriHttpBody(Uri uri, ProgressCallback progressCallback) {
        this(uri, null, progressCallback);
    }

    public ParseCountingUriHttpBody(
        Uri uri, String contentType, ProgressCallback progressCallback) {
        super(uri, contentType);
        this.progressCallback = progressCallback;
    }

    @Override
    public void writeTo(OutputStream output) throws IOException {
        if (output == null) {
            throw new IllegalArgumentException("Output stream may not be null");
        }

        final InputStream fileInput = Parse.getApplicationContext().getContentResolver().openInputStream(uri);
        try {
            byte[] buffer = new byte[DEFAULT_CHUNK_SIZE];
            int n;
            long totalLength = getContentLength();
            long position = 0;
            while (EOF != (n = fileInput.read(buffer))) {
                output.write(buffer, 0, n);
                position += n;

                if (progressCallback != null) {
                    int progress = (int) (100 * position / totalLength);
                    progressCallback.done(progress);
                }
            }
        } finally {
            ParseIOUtils.closeQuietly(fileInput);
        }
    }
}

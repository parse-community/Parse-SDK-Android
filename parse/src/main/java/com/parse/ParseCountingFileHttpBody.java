/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;

class ParseCountingFileHttpBody extends ParseFileHttpBody {

    private static final int DEFAULT_CHUNK_SIZE = 4096;
    private static final int EOF = -1;

    private final ProgressCallback progressCallback;

    public ParseCountingFileHttpBody(File file, ProgressCallback progressCallback) {
        this(file, null, progressCallback);
    }

    public ParseCountingFileHttpBody(
            File file, String contentType, ProgressCallback progressCallback) {
        super(file, contentType);
        this.progressCallback = progressCallback;
    }

    @Override
    public void writeTo(OutputStream output) throws IOException {
        if (output == null) {
            throw new IllegalArgumentException("Output stream may not be null");
        }

        final FileInputStream fileInput = new FileInputStream(file);
        try {
            byte[] buffer = new byte[DEFAULT_CHUNK_SIZE];
            int n;
            long totalLength = file.length();
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

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

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.concurrent.Callable;

import bolts.Task;

/**
 * Request returns a byte array of the response and provides a callback the progress of the data
 * read from the network.
 */
class ParseFileRequest extends ParseRequest<Void> {

    // The temp file is used to save the ParseFile content when we fetch it from server
    private final File tempFile;

    public ParseFileRequest(ParseHttpRequest.Method method, String url, File tempFile) {
        super(method, url);
        this.tempFile = tempFile;
    }

    @Override
    protected Task<Void> onResponseAsync(final ParseHttpResponse response,
                                         final ProgressCallback downloadProgressCallback) {
        int statusCode = response.getStatusCode();
        if (statusCode >= 200 && statusCode < 300 || statusCode == 304) {
            // OK
        } else {
            String action = method == ParseHttpRequest.Method.GET ? "Download from" : "Upload to";
            return Task.forError(new ParseException(ParseException.CONNECTION_FAILED, String.format(
                    "%s file server failed. %s", action, response.getReasonPhrase())));
        }

        if (method != ParseHttpRequest.Method.GET) {
            return null;
        }

        return Task.call(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                long totalSize = response.getTotalSize();
                long downloadedSize = 0;
                InputStream responseStream = null;
                FileOutputStream tempFileStream = null;
                try {
                    responseStream = response.getContent();
                    tempFileStream = ParseFileUtils.openOutputStream(tempFile);

                    int nRead;
                    byte[] data = new byte[32 << 10]; // 32KB

                    while ((nRead = responseStream.read(data, 0, data.length)) != -1) {
                        tempFileStream.write(data, 0, nRead);
                        downloadedSize += nRead;
                        if (downloadProgressCallback != null && totalSize != -1) {
                            int progressToReport =
                                    Math.round((float) downloadedSize / (float) totalSize * 100.0f);
                            downloadProgressCallback.done(progressToReport);
                        }
                    }
                    return null;
                } finally {
                    ParseIOUtils.closeQuietly(responseStream);
                    ParseIOUtils.closeQuietly(tempFileStream);
                }
            }
        }, ParseExecutors.io());
    }
}

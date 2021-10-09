/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import com.parse.boltsinternal.Task;
import com.parse.http.ParseHttpRequest;

import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.CancellationException;

// TODO(grantland): Create ParseFileController interface
class ParseFileController {

    private final Object lock = new Object();
    private final ParseHttpClient restClient;
    private final File cachePath;

    private ParseHttpClient fileClient;

    public ParseFileController(ParseHttpClient restClient, File cachePath) {
        this.restClient = restClient;
        this.cachePath = cachePath;
    }

    /**
     * Gets the file http client if exists, otherwise lazily creates since developers might not always
     * use our download mechanism.
     */
    /* package */ ParseHttpClient fileClient() {
        synchronized (lock) {
            if (fileClient == null) {
                fileClient = ParsePlugins.get().fileClient();
            }
            return fileClient;
        }
    }

    /* package for tests */ ParseFileController fileClient(ParseHttpClient fileClient) {
        synchronized (lock) {
            this.fileClient = fileClient;
        }
        return this;
    }

    public File getCacheFile(ParseFile.State state) {
        return new File(cachePath, state.name());
    }

    /* package for tests */ File getTempFile(ParseFile.State state) {
        if (state.url() == null) {
            return null;
        }
        return new File(cachePath, state.url() + ".tmp");
    }

    public boolean isDataAvailable(ParseFile.State state) {
        return getCacheFile(state).exists();
    }

    public void clearCache() {
        File[] files = cachePath.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            ParseFileUtils.deleteQuietly(file);
        }
    }

    public Task<ParseFile.State> saveAsync(
            final ParseFile.State state,
            final byte[] data,
            String sessionToken,
            ProgressCallback uploadProgressCallback,
            Task<Void> cancellationToken) {
        if (state.url() != null) { // !isDirty
            return Task.forResult(state);
        }
        if (cancellationToken != null && cancellationToken.isCancelled()) {
            return Task.cancelled();
        }

        final ParseRESTCommand command = new ParseRESTFileCommand.Builder()
                .fileName(state.name())
                .data(data)
                .contentType(state.mimeType())
                .sessionToken(sessionToken)
                .build();

        return command.executeAsync(
                restClient,
                uploadProgressCallback,
                null,
                cancellationToken
        ).onSuccess(task -> {
            JSONObject result = task.getResult();
            ParseFile.State newState = new ParseFile.State.Builder(state)
                    .name(result.getString("name"))
                    .url(result.getString("url"))
                    .build();

            // Write data to cache
            try {
                ParseFileUtils.writeByteArrayToFile(getCacheFile(newState), data);
            } catch (IOException e) {
                // do nothing
            }

            return newState;
        }, ParseExecutors.io());
    }

    public Task<ParseFile.State> saveAsync(
            final ParseFile.State state,
            final File file,
            String sessionToken,
            ProgressCallback uploadProgressCallback,
            Task<Void> cancellationToken) {
        if (state.url() != null) { // !isDirty
            return Task.forResult(state);
        }
        if (cancellationToken != null && cancellationToken.isCancelled()) {
            return Task.cancelled();
        }

        final ParseRESTCommand command = new ParseRESTFileCommand.Builder()
                .fileName(state.name())
                .file(file)
                .contentType(state.mimeType())
                .sessionToken(sessionToken)
                .build();

        return command.executeAsync(
                restClient,
                uploadProgressCallback,
                null,
                cancellationToken
        ).onSuccess(task -> {
            JSONObject result = task.getResult();
            ParseFile.State newState = new ParseFile.State.Builder(state)
                    .name(result.getString("name"))
                    .url(result.getString("url"))
                    .build();

            // Write data to cache
            try {
                ParseFileUtils.copyFile(file, getCacheFile(newState));
            } catch (IOException e) {
                // do nothing
            }

            return newState;
        }, ParseExecutors.io());
    }

    public Task<File> fetchAsync(
            final ParseFile.State state,
            @SuppressWarnings("UnusedParameters") String sessionToken,
            final ProgressCallback downloadProgressCallback,
            final Task<Void> cancellationToken) {
        if (cancellationToken != null && cancellationToken.isCancelled()) {
            return Task.cancelled();
        }
        final File cacheFile = getCacheFile(state);
        return Task.call(cacheFile::exists, ParseExecutors.io()).continueWithTask(task -> {
            boolean result = task.getResult();
            if (result) {
                return Task.forResult(cacheFile);
            }
            if (cancellationToken != null && cancellationToken.isCancelled()) {
                return Task.cancelled();
            }

            // Generate the temp file path for caching ParseFile content based on ParseFile's url
            // The reason we do not write to the cacheFile directly is because there is no way we can
            // verify if a cacheFile is complete or not. If download is interrupted in the middle, next
            // time when we download the ParseFile, since cacheFile has already existed, we will return
            // this incomplete cacheFile
            final File tempFile = getTempFile(state);

            // network
            final ParseFileRequest request =
                    new ParseFileRequest(ParseHttpRequest.Method.GET, state.url(), tempFile);

            // We do not need to delete the temp file since we always try to overwrite it
            return request.executeAsync(
                    fileClient(),
                    null,
                    downloadProgressCallback,
                    cancellationToken).continueWithTask(task1 -> {
                // If the top-level task was cancelled, don't actually set the data -- just move on.
                if (cancellationToken != null && cancellationToken.isCancelled()) {
                    throw new CancellationException();
                }
                if (task1.isFaulted()) {
                    ParseFileUtils.deleteQuietly(tempFile);
                    return task1.cast();
                }

                // Since we give the cacheFile pointer to developers, it is not safe to guarantee
                // cacheFile always does not exist here, so it is better to delete it manually,
                // otherwise moveFile may throw an exception.
                ParseFileUtils.deleteQuietly(cacheFile);
                ParseFileUtils.moveFile(tempFile, cacheFile);
                return Task.forResult(cacheFile);
            }, ParseExecutors.io());
        });
    }
}

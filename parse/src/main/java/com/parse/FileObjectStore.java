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

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;

class FileObjectStore<T extends ParseObject> implements ParseObjectStore<T> {

    private final String className;
    private final File file;
    private final ParseObjectCurrentCoder coder;

    public FileObjectStore(Class<T> clazz, File file, ParseObjectCurrentCoder coder) {
        this(getSubclassingController().getClassName(clazz), file, coder);
    }

    public FileObjectStore(String className, File file, ParseObjectCurrentCoder coder) {
        this.className = className;
        this.file = file;
        this.coder = coder;
    }

    private static ParseObjectSubclassingController getSubclassingController() {
        return ParseCorePlugins.getInstance().getSubclassingController();
    }

    /**
     * Saves the {@code ParseObject} to the a file on disk as JSON in /2/ format.
     *
     * @param coder   Current coder to encode the ParseObject.
     * @param current ParseObject which needs to be saved to disk.
     * @param file    The file to save the object to.
     * @see #getFromDisk(ParseObjectCurrentCoder, File, ParseObject.State.Init)
     */
    private static void saveToDisk(
            ParseObjectCurrentCoder coder, ParseObject current, File file) {
        JSONObject json = coder.encode(current.getState(), null, PointerEncoder.get());
        try {
            ParseFileUtils.writeJSONObjectToFile(file, json);
        } catch (IOException e) {
            //TODO(grantland): We should do something if this fails...
        }
    }

    /**
     * Retrieves a {@code ParseObject} from a file on disk in /2/ format.
     *
     * @param coder   Current coder to decode the ParseObject.
     * @param file    The file to retrieve the object from.
     * @param builder An empty builder which is used to generate a empty state and rebuild a ParseObject.
     * @return The {@code ParseObject} that was retrieved. If the file wasn't found, or the contents
     * of the file is an invalid {@code ParseObject}, returns {@code null}.
     * @see #saveToDisk(ParseObjectCurrentCoder, ParseObject, File)
     */
    private static <T extends ParseObject> T getFromDisk(
            ParseObjectCurrentCoder coder, File file, ParseObject.State.Init builder) {
        JSONObject json;
        try {
            json = ParseFileUtils.readFileToJSONObject(file);
        } catch (IOException | JSONException e) {
            return null;
        }

        ParseObject.State newState = coder.decode(builder, json, ParseDecoder.get())
                .isComplete(true)
                .build();
        return ParseObject.from(newState);
    }

    @Override
    public Task<Void> setAsync(final T object) {
        return Task.call(() -> {
            saveToDisk(coder, object, file);
            //TODO (grantland): check to see if this failed? We currently don't for legacy reasons.
            return null;
        }, ParseExecutors.io());
    }

    @Override
    public Task<T> getAsync() {
        return Task.call(() -> {
            if (!file.exists()) {
                return null;
            }
            return getFromDisk(coder, file, ParseObject.State.newBuilder(className));
        }, ParseExecutors.io());
    }

    @Override
    public Task<Boolean> existsAsync() {
        return Task.call(file::exists, ParseExecutors.io());
    }

    @Override
    public Task<Void> deleteAsync() {
        return Task.call(() -> {
            if (file.exists() && !ParseFileUtils.deleteQuietly(file)) {
                throw new RuntimeException("Unable to delete");
            }

            return null;
        }, ParseExecutors.io());
    }
}

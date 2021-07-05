/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse

import com.parse.PointerEncoder.Companion.get
import com.parse.boltsinternal.Task
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException

internal class FileObjectStore<T : ParseObject>(
    private val className: String,
    private val file: File,
    private val coder: ParseObjectCurrentCoder
) : ParseObjectStore<T> {
    constructor(clazz: Class<T>?, file: File, coder: ParseObjectCurrentCoder) : this(
        subclassingController.getClassName(clazz), file, coder
    )

    override fun setAsync(`object`: T): Task<Void> {
        return Task.call({
            saveToDisk(coder, `object`, file)
            null
        }, ParseExecutors.io())
    }

    override val getAsync: Task<T>
        get() = Task.call({
            if (!file.exists()) {
                return@call null
            }
            getFromDisk(coder, file, ParseObject.State.newBuilder(className))
        }, ParseExecutors.io())

    override fun existsAsync(): Task<Boolean> {
        return Task.call({ file.exists() }, ParseExecutors.io())
    }

    override fun deleteAsync(): Task<Void> {
        return Task.call({
            if (file.exists() && !ParseFileUtils.deleteQuietly(file)) {
                throw RuntimeException("Unable to delete")
            }
            null
        }, ParseExecutors.io())
    }

    companion object {
        private val subclassingController: ParseObjectSubclassingController
            get() = ParseCorePlugins.getInstance().subclassingController

        /**
         * Saves the `ParseObject` to the a file on disk as JSON in /2/ format.
         *
         * @param coder   Current coder to encode the ParseObject.
         * @param current ParseObject which needs to be saved to disk.
         * @param file    The file to save the object to.
         * @see .getFromDisk
         */
        private fun saveToDisk(
            coder: ParseObjectCurrentCoder, current: ParseObject, file: File
        ) {
            val json = coder.encode(current.state, null, get())!!
            try {
                ParseFileUtils.writeJSONObjectToFile(file, json)
            } catch (e: IOException) {
                //TODO(grantland): We should do something if this fails...
            }
        }

        /**
         * Retrieves a `ParseObject` from a file on disk in /2/ format.
         *
         * @param coder   Current coder to decode the ParseObject.
         * @param file    The file to retrieve the object from.
         * @param builder An empty builder which is used to generate a empty state and rebuild a ParseObject.
         * @return The `ParseObject` that was retrieved. If the file wasn't found, or the contents
         * of the file is an invalid `ParseObject`, returns `null`.
         * @see .saveToDisk
         */
        private fun <T : ParseObject?> getFromDisk(
            coder: ParseObjectCurrentCoder, file: File, builder: ParseObject.State.Init<*>
        ): T? {
            val json: JSONObject = try {
                ParseFileUtils.readFileToJSONObject(file)
            } catch (e: IOException) {
                return null
            } catch (e: JSONException) {
                return null
            }
            val newState: ParseObject.State = coder.decode(builder, json, ParseDecoder.get())
                .isComplete(true)
                .build()
            return ParseObject.from(newState)
        }
    }
}
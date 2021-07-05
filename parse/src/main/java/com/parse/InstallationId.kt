/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse

import com.parse.PLog.e
import com.parse.PLog.i
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.*

/**
 * Since we cannot save dirty ParseObjects to disk and we must be able to persist UUIDs across
 * restarts even if the ParseInstallation is not saved, we use this legacy file still as a
 * bootstrapping environment as well until the full ParseInstallation is cached to disk.
 *
 *
 * TODO: Allow dirty objects to be saved to disk.
 */
class InstallationId(private val file: File) {
    private val lock = Any()
    private var installationId: String? = null

    /**
     * Loads the installationId from memory, then tries to loads the legacy installationId from disk
     * if it is present, or creates a new random UUID.
     */
    fun get(): String? {
        synchronized(lock) {
            if (installationId == null) {
                try {
                    installationId = ParseFileUtils.readFileToString(file, "UTF-8")
                } catch (e: FileNotFoundException) {
                    i(TAG, "Couldn't find existing installationId file. Creating one instead.")
                } catch (e: IOException) {
                    e(TAG, "Unexpected exception reading installation id from disk", e)
                }
            }
            if (installationId == null) {
                setInternal(UUID.randomUUID().toString())
            }
        }
        return installationId
    }

    /**
     * Sets the installationId and persists it to disk.
     */
    fun set(newInstallationId: String) {
        synchronized(lock) {
            if (ParseTextUtils.isEmpty(newInstallationId)
                || newInstallationId == get()
            ) {
                return
            }
            setInternal(newInstallationId)
        }
    }

    private fun setInternal(newInstallationId: String) {
        synchronized(lock) {
            try {
                ParseFileUtils.writeStringToFile(file, newInstallationId, "UTF-8")
            } catch (e: IOException) {
                e(TAG, "Unexpected exception writing installation id to disk", e)
            }
            installationId = newInstallationId
        }
    }

    /* package for tests */
    fun clear() {
        synchronized(lock) {
            installationId = null
            ParseFileUtils.deleteQuietly(file)
        }
    }

    companion object {
        private const val TAG = "InstallationId"
    }
}
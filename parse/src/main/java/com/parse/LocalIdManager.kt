/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse

import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.*

/**
 * Manages a set of local ids and possible mappings to global Parse objectIds. This class is
 * thread-safe.
 */
internal class LocalIdManager(root: File?) {
    // Path to the local id storage on disk.
    private val diskPath: File = File(root, "LocalId")

    // Random generator for inventing new ids.
    private val random: Random = Random()

    /**
     * Returns true if localId has the right basic format for a local id.
     */
    private fun isLocalId(localId: String): Boolean {
        if (!localId.startsWith("local_")) {
            return false
        }
        for (i in 6 until localId.length) {
            val c = localId[i]
            if (c !in '0'..'9' && c !in 'a'..'f') {
                return false
            }
        }
        return true
    }

    /**
     * Grabs one entry in the local id map off the disk.
     */
    @Synchronized
    private fun getMapEntry(localId: String): MapEntry {
        check(isLocalId(localId)) { "Tried to get invalid local id: \"$localId\"." }
        return try {
            val json = ParseFileUtils.readFileToJSONObject(File(diskPath, localId))
            val entry = MapEntry()
            entry.retainCount = json.optInt("retainCount", 0)
            entry.objectId = json.optString("objectId", null)
            entry
        } catch (e: IOException) {
            MapEntry()
        } catch (e: JSONException) {
            MapEntry()
        }
    }

    /**
     * Writes one entry to the local id map on disk.
     */
    @Synchronized
    private fun putMapEntry(localId: String, entry: MapEntry) {
        check(isLocalId(localId)) { "Tried to get invalid local id: \"$localId\"." }
        val json = JSONObject()
        try {
            json.put("retainCount", entry.retainCount)
            if (entry.objectId != null) {
                json.put("objectId", entry.objectId)
            }
        } catch (je: JSONException) {
            throw IllegalStateException("Error creating local id map entry.", je)
        }
        val file = File(diskPath, localId)
        if (!diskPath.exists()) {
            diskPath.mkdirs()
        }
        try {
            ParseFileUtils.writeJSONObjectToFile(file, json)
        } catch (e: IOException) {
            //TODO (grantland): We should do something if this fails...
        }
    }

    /**
     * Removes an entry from the local id map on disk.
     */
    @Synchronized
    private fun removeMapEntry(localId: String) {
        check(isLocalId(localId)) { "Tried to get invalid local id: \"$localId\"." }
        val file = File(diskPath, localId)
        ParseFileUtils.deleteQuietly(file)
    }

    /**
     * Creates a new local id.
     */
    @Synchronized
    fun createLocalId(): String {
        val localIdNumber = random.nextLong()
        val localId = "local_" + java.lang.Long.toHexString(localIdNumber)
        check(isLocalId(localId)) {
            ("Generated an invalid local id: \"" + localId + "\". "
                    + "This should never happen. Open a bug at https://github.com/parse-community/parse-server")
        }
        return localId
    }

    /**
     * Increments the retain count of a local id on disk.
     */
    @Synchronized
    fun retainLocalIdOnDisk(localId: String) {
        val entry = getMapEntry(localId)
        entry.retainCount++
        putMapEntry(localId, entry)
    }

    /**
     * Decrements the retain count of a local id on disk. If the retain count hits zero, the id is
     * forgotten forever.
     */
    @Synchronized
    fun releaseLocalIdOnDisk(localId: String) {
        val entry = getMapEntry(localId)
        entry.retainCount--
        if (entry.retainCount > 0) {
            putMapEntry(localId, entry)
        } else {
            removeMapEntry(localId)
        }
    }

    /**
     * Returns the objectId associated with a given local id. Returns null if no objectId is yet known
     * for the local id.
     */
    @Synchronized
    fun getObjectId(localId: String): String? {
        val entry = getMapEntry(localId)
        return entry.objectId
    }

    /**
     * Sets the objectId associated with a given local id.
     */
    @Synchronized
    fun setObjectId(localId: String, objectId: String?) {
        val entry = getMapEntry(localId)
        if (entry.retainCount > 0) {
            check(entry.objectId == null) { "Tried to set an objectId for a localId that already has one." }
            entry.objectId = objectId
            putMapEntry(localId, entry)
        }
    }

    /**
     * Clears all local ids from the map. Returns true is the cache was already empty.
     */
    @Synchronized
    @Throws(IOException::class)
    fun clear(): Boolean {
        val files = diskPath.list() ?: return false
        if (files.isEmpty()) {
            return false
        }
        for (fileName in files) {
            val file = File(diskPath, fileName)
            if (!file.delete()) {
                throw IOException("Unable to delete file $fileName in localId cache.")
            }
        }
        return true
    }

    /**
     * Internal class representing all the information we know about a local id.
     */
    private class MapEntry {
        var objectId: String? = null
        var retainCount = 0
    }

}
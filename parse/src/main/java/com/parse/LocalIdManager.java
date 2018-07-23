/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.Random;

/**
 * Manages a set of local ids and possible mappings to global Parse objectIds. This class is
 * thread-safe.
 */
class LocalIdManager {

    // Path to the local id storage on disk.
    private final File diskPath;
    // Random generator for inventing new ids.
    private final Random random;

    /**
     * Creates a new LocalIdManager with default options.
     */
    /* package for tests */ LocalIdManager(File root) {
        diskPath = new File(root, "LocalId");
        random = new Random();
    }

    /**
     * Returns true if localId has the right basic format for a local id.
     */
    private boolean isLocalId(String localId) {
        if (!localId.startsWith("local_")) {
            return false;
        }
        for (int i = 6; i < localId.length(); ++i) {
            char c = localId.charAt(i);
            if (!(c >= '0' && c <= '9') && !(c >= 'a' && c <= 'f')) {
                return false;
            }
        }
        return true;
    }

    /**
     * Grabs one entry in the local id map off the disk.
     */
    private synchronized MapEntry getMapEntry(String localId) {
        if (!isLocalId(localId)) {
            throw new IllegalStateException("Tried to get invalid local id: \"" + localId + "\".");
        }

        try {
            JSONObject json = ParseFileUtils.readFileToJSONObject(new File(diskPath, localId));

            MapEntry entry = new MapEntry();
            entry.retainCount = json.optInt("retainCount", 0);
            entry.objectId = json.optString("objectId", null);
            return entry;
        } catch (IOException | JSONException e) {
            return new MapEntry();
        }
    }

    /**
     * Writes one entry to the local id map on disk.
     */
    private synchronized void putMapEntry(String localId, MapEntry entry) {
        if (!isLocalId(localId)) {
            throw new IllegalStateException("Tried to get invalid local id: \"" + localId + "\".");
        }

        JSONObject json = new JSONObject();
        try {
            json.put("retainCount", entry.retainCount);
            if (entry.objectId != null) {
                json.put("objectId", entry.objectId);
            }
        } catch (JSONException je) {
            throw new IllegalStateException("Error creating local id map entry.", je);
        }

        File file = new File(diskPath, localId);
        if (!diskPath.exists()) {
            diskPath.mkdirs();
        }

        try {
            ParseFileUtils.writeJSONObjectToFile(file, json);
        } catch (IOException e) {
            //TODO (grantland): We should do something if this fails...
        }
    }

    /**
     * Removes an entry from the local id map on disk.
     */
    private synchronized void removeMapEntry(String localId) {
        if (!isLocalId(localId)) {
            throw new IllegalStateException("Tried to get invalid local id: \"" + localId + "\".");
        }
        File file = new File(diskPath, localId);
        ParseFileUtils.deleteQuietly(file);
    }

    /**
     * Creates a new local id.
     */
    synchronized String createLocalId() {
        long localIdNumber = random.nextLong();
        String localId = "local_" + Long.toHexString(localIdNumber);

        if (!isLocalId(localId)) {
            throw new IllegalStateException("Generated an invalid local id: \"" + localId + "\". "
                    + "This should never happen. Open a bug at https://github.com/parse-community/parse-server");
        }

        return localId;
    }

    /**
     * Increments the retain count of a local id on disk.
     */
    synchronized void retainLocalIdOnDisk(String localId) {
        MapEntry entry = getMapEntry(localId);
        entry.retainCount++;
        putMapEntry(localId, entry);
    }

    /**
     * Decrements the retain count of a local id on disk. If the retain count hits zero, the id is
     * forgotten forever.
     */
    synchronized void releaseLocalIdOnDisk(String localId) {
        MapEntry entry = getMapEntry(localId);
        entry.retainCount--;

        if (entry.retainCount > 0) {
            putMapEntry(localId, entry);
        } else {
            removeMapEntry(localId);
        }
    }

    /**
     * Returns the objectId associated with a given local id. Returns null if no objectId is yet known
     * for the local id.
     */
    synchronized String getObjectId(String localId) {
        MapEntry entry = getMapEntry(localId);
        return entry.objectId;
    }

    /**
     * Sets the objectId associated with a given local id.
     */
    synchronized void setObjectId(String localId, String objectId) {
        MapEntry entry = getMapEntry(localId);
        if (entry.retainCount > 0) {
            if (entry.objectId != null) {
                throw new IllegalStateException(
                        "Tried to set an objectId for a localId that already has one.");
            }
            entry.objectId = objectId;
            putMapEntry(localId, entry);
        }
    }

    /**
     * Clears all local ids from the map. Returns true is the cache was already empty.
     */
    synchronized boolean clear() throws IOException {
        String[] files = diskPath.list();
        if (files == null) {
            return false;
        }
        if (files.length == 0) {
            return false;
        }
        for (String fileName : files) {
            File file = new File(diskPath, fileName);
            if (!file.delete()) {
                throw new IOException("Unable to delete file " + fileName + " in localId cache.");
            }
        }
        return true;
    }

    /**
     * Internal class representing all the information we know about a local id.
     */
    private static class MapEntry {
        String objectId;
        int retainCount;
    }
}

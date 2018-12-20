/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import android.content.Context;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;

/**
 * Used for ParseQuery caching.
 */
class ParseKeyValueCache {

    // We limit the cache to 2MB because that's about what the default browser
    // uses.
    /* package */ static final int DEFAULT_MAX_KEY_VALUE_CACHE_BYTES = 2 * 1024 * 1024;
    // We limit to 1000 cache files to avoid taking too long while scanning the
    // cache
    /* package */ static final int DEFAULT_MAX_KEY_VALUE_CACHE_FILES = 1000;
    private static final String TAG = "ParseKeyValueCache";
    private static final String DIR_NAME = "ParseKeyValueCache";
    /**
     * Prevent multiple threads from modifying the cache at the same time.
     */
    private static final Object MUTEX_IO = new Object();

    /* package */ static int maxKeyValueCacheBytes = DEFAULT_MAX_KEY_VALUE_CACHE_BYTES;
    /* package */ static int maxKeyValueCacheFiles = DEFAULT_MAX_KEY_VALUE_CACHE_FILES;

    private static File directory;

    // Creates a directory to keep cache-type files in.
    // The operating system will automatically clear out these files first
    // when space gets low.
    /* package */
    static void initialize(Context context) {
        initialize(new File(context.getCacheDir(), DIR_NAME));
    }

    /* package for tests */
    static void initialize(File path) {
        if (!path.isDirectory() && !path.mkdir()) {
            throw new RuntimeException("Could not create ParseKeyValueCache directory");
        }
        directory = path;
    }

    private static File getKeyValueCacheDir() {
        if (directory != null && !directory.exists()) {
            directory.mkdir();
        }
        return directory;
    }

    /**
     * How many files are in the key-value cache.
     */
    /* package */
    static int size() {
        File[] files = getKeyValueCacheDir().listFiles();
        if (files == null) {
            return 0;
        }
        return files.length;
    }

    private static File getKeyValueCacheFile(String key) {
        final String suffix = '.' + key;
        File[] matches = getKeyValueCacheDir().listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String filename) {
                return filename.endsWith(suffix);
            }
        });
        return (matches == null || matches.length == 0) ? null : matches[0];
    }

    // Badly formatted files return the epoch
    private static long getKeyValueCacheAge(File cacheFile) {
        // Format: <date>.<key>
        String name = cacheFile.getName();
        try {
            return Long.parseLong(name.substring(0, name.indexOf('.')));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static File createKeyValueCacheFile(String key) {
        String filename = String.valueOf(new Date().getTime()) + '.' + key;
        return new File(getKeyValueCacheDir(), filename);
    }

    // Removes all the cache entries.
    /* package */
    static void clearKeyValueCacheDir() {
        synchronized (MUTEX_IO) {
            File dir = getKeyValueCacheDir();
            if (dir == null) {
                return;
            }
            File[] entries = dir.listFiles();
            if (entries == null) {
                return;
            }
            for (File entry : entries) {
                entry.delete();
            }
        }
    }

    // Saves a key-value pair to the cache
    /* package */
    static void saveToKeyValueCache(String key, String value) {
        synchronized (MUTEX_IO) {
            File prior = getKeyValueCacheFile(key);
            if (prior != null) {
                prior.delete();
            }
            File f = createKeyValueCacheFile(key);
            try {
                ParseFileUtils.writeByteArrayToFile(f, value.getBytes("UTF-8"));
            } catch (IOException e) {
                // do nothing
            }

            // Check if we should kick out old cache entries
            File[] files = getKeyValueCacheDir().listFiles();
            // We still need this check since dir.mkdir() may fail
            if (files == null || files.length == 0) {
                return;
            }

            int numFiles = files.length;
            int numBytes = 0;
            for (File file : files) {
                numBytes += file.length();
            }

            // If we do not need to clear the cache, simply return
            if (numFiles <= maxKeyValueCacheFiles && numBytes <= maxKeyValueCacheBytes) {
                return;
            }

            // We need to kick out some cache entries.
            // Sort oldest-first. We touch on read so mtime is really LRU.
            // Sometimes (i.e. tests) the time of lastModified isn't granular enough,
            // so we resort
            // to sorting by the file name which is always prepended with time in ms
            Arrays.sort(files, new Comparator<File>() {
                @Override
                public int compare(File f1, File f2) {
                    int dateCompare = Long.valueOf(f1.lastModified()).compareTo(f2.lastModified());
                    if (dateCompare != 0) {
                        return dateCompare;
                    } else {
                        return f1.getName().compareTo(f2.getName());
                    }
                }
            });

            for (File file : files) {
                numFiles--;
                numBytes -= file.length();
                file.delete();

                if (numFiles <= maxKeyValueCacheFiles && numBytes <= maxKeyValueCacheBytes) {
                    break;
                }
            }
        }
    }

    // Clears a key from the cache if it's there. If it's not there, this is a
    // no-op.
    /* package */
    static void clearFromKeyValueCache(String key) {
        synchronized (MUTEX_IO) {
            File file = getKeyValueCacheFile(key);
            if (file != null) {
                file.delete();
            }
        }
    }

    // Loads a value from the key-value cache.
    // Returns null if nothing is there.
    /* package */
    static String loadFromKeyValueCache(final String key, final long maxAgeMilliseconds) {
        synchronized (MUTEX_IO) {
            File file = getKeyValueCacheFile(key);
            if (file == null) {
                return null;
            }

            Date now = new Date();
            long oldestAcceptableAge = Math.max(0, now.getTime() - maxAgeMilliseconds);
            if (getKeyValueCacheAge(file) < oldestAcceptableAge) {
                return null;
            }

            // Update mtime to make the LRU work
            file.setLastModified(now.getTime());

            try {
                RandomAccessFile f = new RandomAccessFile(file, "r");
                byte[] bytes = new byte[(int) f.length()];
                f.readFully(bytes);
                f.close();
                return new String(bytes, "UTF-8");
            } catch (IOException e) {
                PLog.e(TAG, "error reading from cache", e);
                return null;
            }
        }
    }

    // Returns null if the value does not exist or is not json
    /* package */
    static JSONObject jsonFromKeyValueCache(String key, long maxAgeMilliseconds) {
        String raw = loadFromKeyValueCache(key, maxAgeMilliseconds);
        if (raw == null) {
            return null;
        }

        try {
            return new JSONObject(raw);
        } catch (JSONException e) {
            PLog.e(TAG, "corrupted cache for " + key, e);
            clearFromKeyValueCache(key);
            return null;
        }
    }
}

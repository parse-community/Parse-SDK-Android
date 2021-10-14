/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.parse.boltsinternal.Task;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ParseKeyValueCacheTest {

    @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();
    private File keyValueCacheDir;

    @Before
    public void setUp() throws Exception {
        keyValueCacheDir = temporaryFolder.newFolder("ParseKeyValueCache");
        ParseKeyValueCache.initialize(keyValueCacheDir);
    }

    @After
    public void tearDown() {
        ParseKeyValueCache.clearKeyValueCacheDir();
        ParseKeyValueCache.maxKeyValueCacheBytes =
                ParseKeyValueCache.DEFAULT_MAX_KEY_VALUE_CACHE_BYTES;
        ParseKeyValueCache.maxKeyValueCacheFiles =
                ParseKeyValueCache.DEFAULT_MAX_KEY_VALUE_CACHE_FILES;
    }

    @Test
    public void testMultipleAsynchronousWrites() throws ParseException {
        int max = 100;
        ParseKeyValueCache.maxKeyValueCacheFiles = max;

        // Max out KeyValueCache
        for (int i = 0; i < max; i++) {
            ParseKeyValueCache.saveToKeyValueCache("key " + i, "test");
        }

        List<Task<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            tasks.add(
                    Task.call(
                            () -> {
                                ParseKeyValueCache.saveToKeyValueCache("foo", "test");
                                return null;
                            },
                            Task.BACKGROUND_EXECUTOR));
        }
        ParseTaskUtils.wait(Task.whenAll(tasks));
    }

    @Test
    public void testSaveToKeyValueCacheWithoutCacheDir() throws Exception {
        // Delete the cache folder(Simulate users clear the app cache)
        assertTrue(keyValueCacheDir.exists());
        keyValueCacheDir.delete();
        assertFalse(keyValueCacheDir.exists());

        // Save a key value pair
        ParseKeyValueCache.saveToKeyValueCache("key", "value");

        // Verify cache file is correct
        assertEquals(1, keyValueCacheDir.listFiles().length);
        assertArrayEquals(
                "value".getBytes(),
                ParseFileUtils.readFileToByteArray(keyValueCacheDir.listFiles()[0]));
    }

    @Test
    public void testGetSizeWithoutCacheDir() {
        // Delete the cache folder(Simulate users clear the app cache)
        assertTrue(keyValueCacheDir.exists());
        keyValueCacheDir.delete();
        assertFalse(keyValueCacheDir.exists());

        // Verify size is zero
        assertEquals(0, ParseKeyValueCache.size());
    }
}

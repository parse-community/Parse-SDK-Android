/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import bolts.Task;

public class ParseKeyValueCacheTest {

  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Before
  public void setUp() throws Exception {
    ParseKeyValueCache.initialize(temporaryFolder.newFolder("ParseKeyValueCache"));
  }

  @After
  public void tearDown() throws Exception {
    ParseKeyValueCache.clearKeyValueCacheDir();
    ParseKeyValueCache.maxKeyValueCacheBytes = ParseKeyValueCache.DEFAULT_MAX_KEY_VALUE_CACHE_BYTES;
    ParseKeyValueCache.maxKeyValueCacheFiles = ParseKeyValueCache.DEFAULT_MAX_KEY_VALUE_CACHE_FILES;
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
      tasks.add(Task.call(new Callable<Void>() {
        @Override
        public Void call() throws Exception {
          ParseKeyValueCache.saveToKeyValueCache("foo", "test");
          return null;
        }
      }, Task.BACKGROUND_EXECUTOR));
    }
    ParseTaskUtils.wait(Task.whenAll(tasks));
  }
}

/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import android.webkit.MimeTypeMap;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.robolectric.Shadows.shadowOf;

// For android.webkit.MimeTypeMap
@RunWith(RobolectricTestRunner.class)
@Config(constants = BuildConfig.class, sdk = TestHelper.ROBOLECTRIC_SDK_VERSION)
public class ParseFileStateTest {

  @Before
  public void setUp() {
    shadowOf(MimeTypeMap.getSingleton()).addExtensionMimeTypMapping("txt", "text/plain");
  }

  @After
  public void tearDown() {
    shadowOf(MimeTypeMap.getSingleton()).clearMappings();
  }

  @Test
  public void testDefaults() {
    ParseFile.State state = new ParseFile.State.Builder().build();
    assertEquals("file", state.name());
    assertEquals(null, state.mimeType());
    assertNull(state.url());
  }

  @Test
  public void testProperties() {
    ParseFile.State state = new ParseFile.State.Builder()
        .name("test")
        .mimeType("application/test")
        .url("http://twitter.com/grantland")
        .build();
    assertEquals("test", state.name());
    assertEquals("application/test", state.mimeType());
    assertEquals("http://twitter.com/grantland", state.url());
  }

  @Test
  public void testCopy() {
    ParseFile.State state = new ParseFile.State.Builder()
        .name("test")
        .mimeType("application/test")
        .url("http://twitter.com/grantland")
        .build();
    ParseFile.State copy = new ParseFile.State.Builder(state).build();
    assertEquals("test", copy.name());
    assertEquals("application/test", copy.mimeType());
    assertEquals("http://twitter.com/grantland", copy.url());
    assertNotSame(state, copy);
  }

  @Test
  public void testMimeType() {
    ParseFile.State state = new ParseFile.State.Builder()
        .mimeType("test")
        .build();
    assertEquals("test", state.mimeType());
  }

  @Test
  public void testMimeTypeNotSetFromExtension() {
  ParseFile.State state = new ParseFile.State.Builder()
        .name("test.txt")
        .build();
    assertEquals(null, state.mimeType());
  }
}

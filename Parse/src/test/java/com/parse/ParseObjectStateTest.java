/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import org.junit.Test;

import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.contains;

public class ParseObjectStateTest {

  @Test
  public void testDefaults() {
    ParseObject.State state = new ParseObject.State.Builder("TestObject").build();
    assertEquals("TestObject", state.className());
    assertNull(state.objectId());
    assertEquals(-1, state.createdAt());
    assertEquals(-1, state.updatedAt());
    assertFalse(state.isComplete());
    assertTrue(state.keySet().isEmpty());
  }

  @Test
  public void testProperties() {
    long updatedAt = System.currentTimeMillis();
    long createdAt = updatedAt + 10;

    ParseObject.State state = new ParseObject.State.Builder("TestObject")
        .objectId("fake")
        .createdAt(new Date(createdAt))
        .updatedAt(new Date(updatedAt))
        .isComplete(true)
        .build();
    assertEquals("TestObject", state.className());
    assertEquals("fake", state.objectId());
    assertEquals(createdAt, state.createdAt());
    assertEquals(updatedAt, state.updatedAt());
    assertTrue(state.isComplete());
  }

  @Test
  public void testCopy() {
    long updatedAt = System.currentTimeMillis();
    long createdAt = updatedAt + 10;

    ParseObject.State state = new ParseObject.State.Builder("TestObject")
        .objectId("fake")
        .createdAt(new Date(createdAt))
        .updatedAt(new Date(updatedAt))
        .isComplete(true)
        .put("foo", "bar")
        .put("baz", "qux")
        .build();
    ParseObject.State copy = new ParseObject.State.Builder(state).build();
    assertEquals(state.className(), copy.className());
    assertEquals(state.objectId(), copy.objectId());
    assertEquals(state.createdAt(), copy.createdAt());
    assertEquals(state.updatedAt(), copy.updatedAt());
    assertEquals(state.isComplete(), copy.isComplete());
    assertEquals(state.keySet().size(), copy.keySet().size());
    assertEquals(state.get("foo"), copy.get("foo"));
    assertEquals(state.get("baz"), copy.get("baz"));
  }

  @Test
  public void testAutomaticUpdatedAt() {
    long createdAt = System.currentTimeMillis();

    ParseObject.State state = new ParseObject.State.Builder("TestObject")
        .createdAt(new Date(createdAt))
        .build();
    assertEquals(createdAt, state.createdAt());
    assertEquals(createdAt, state.updatedAt());
  }

  @Test
  public void testServerData() {
    ParseObject.State.Builder builder = new ParseObject.State.Builder("TestObject");
    ParseObject.State state = builder.build();
    assertTrue(state.keySet().isEmpty());

    builder.put("foo", "bar")
        .put("baz", "qux");
    state = builder.build();
    assertEquals(2, state.keySet().size());
    assertEquals("bar", state.get("foo"));
    assertEquals("qux", state.get("baz"));

    builder.remove("foo");
    state = builder.build();
    assertEquals(1, state.keySet().size());
    assertNull(state.get("foo"));
    assertEquals("qux", state.get("baz"));

    builder.clear();
    state = builder.build();
    assertTrue(state.keySet().isEmpty());
    assertNull(state.get("foo"));
    assertNull(state.get("baz"));
  }

  @Test
  public void testToString() {
    String string = new ParseObject.State.Builder("TestObject").build().toString();
    assertTrue(string.contains("com.parse.ParseObject$State"));
    assertTrue(string.contains("className"));
    assertTrue(string.contains("objectId"));
    assertTrue(string.contains("createdAt"));
    assertTrue(string.contains("updatedAt"));
    assertTrue(string.contains("isComplete"));
    assertTrue(string.contains("serverData"));
  }
}

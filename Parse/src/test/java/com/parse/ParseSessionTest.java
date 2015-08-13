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
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertTrue;

public class ParseSessionTest {

  @Before
  public void setUp() {
    ParseObject.registerSubclass(ParseSession.class);
  }

  @After
  public void tearDown() {
    ParseObject.unregisterSubclass(ParseSession.class);
  }

  @Test
  public void testImmutableKeys() {
    String[] immutableKeys = {
        "sessionToken",
        "createdWith",
        "restricted",
        "user",
        "expiresAt",
        "installationId"
    };

    ParseSession session = new ParseSession();
    session.put("foo", "bar");
    session.put("USER", "bar");
    session.put("_user", "bar");
    session.put("token", "bar");

    for (String immutableKey : immutableKeys) {
      try {
        session.put(immutableKey, "blah");
      } catch (IllegalArgumentException e) {
        assertTrue(e.getMessage().contains("Cannot modify"));
      }

      try {
        session.remove(immutableKey);
      } catch (IllegalArgumentException e) {
        assertTrue(e.getMessage().contains("Cannot modify"));
      }

      try {
        session.removeAll(immutableKey, Arrays.asList());
      } catch (IllegalArgumentException e) {
        assertTrue(e.getMessage().contains("Cannot modify"));
      }
    }
  }
}

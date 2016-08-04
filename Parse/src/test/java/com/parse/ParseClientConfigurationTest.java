/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import com.parse.http.ParseNetworkInterceptor;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;

public class ParseClientConfigurationTest {

  @Test
  public void testBuilder() {
    Parse.Configuration.Builder builder = new Parse.Configuration.Builder(null);
    builder.applicationId("foo");
    builder.clientKey("bar");
    builder.enableLocalDataStore();
    Parse.Configuration configuration = builder.build();

    assertNull(configuration.context);
    assertEquals(configuration.applicationId, "foo");
    assertEquals(configuration.clientKey, "bar");
    assertEquals(configuration.localDataStoreEnabled, true);
  }

  @Test
  public void testBuilderServerURL() {
    Parse.Configuration.Builder builder = new Parse.Configuration.Builder(null);
    builder.server("http://myserver.com/parse/");
    Parse.Configuration configuration = builder.build();
    assertEquals(configuration.server, "http://myserver.com/parse/");
  }

  @Test
  public void testBuilderServerMissingSlashURL() {
    Parse.Configuration.Builder builder = new Parse.Configuration.Builder(null);
    builder.server("http://myserver.com/missingslash");
    Parse.Configuration configuration = builder.build();
    assertEquals(configuration.server, "http://myserver.com/missingslash/");
  }

  @Test
  public void testNetworkInterceptors() {
    ParseNetworkInterceptor interceptorA = mock(ParseNetworkInterceptor.class);
    ParseNetworkInterceptor interceptorB = mock(ParseNetworkInterceptor.class);

    Parse.Configuration.Builder builder = new Parse.Configuration.Builder(null);

    builder.addNetworkInterceptor(interceptorA);
    Parse.Configuration configurationA = builder.build();
    builder.addNetworkInterceptor(interceptorB);
    Parse.Configuration configurationB = builder.build();

    assertFalse(configurationA.interceptors.contains(interceptorB));
    assertTrue(configurationB.interceptors.contains(interceptorB));

    try {
      configurationA.interceptors.add(interceptorB);
      fail("Interceptors shouldn't be mutable.");
    } catch (UnsupportedOperationException ex) {
      // Expected
    }
  }

  @Test
  public void testSetNetworkInterceptors() {
    final ParseNetworkInterceptor interceptorA = mock(ParseNetworkInterceptor.class);
    final ParseNetworkInterceptor interceptorB = mock(ParseNetworkInterceptor.class);

    Collection<ParseNetworkInterceptor> collectionA = new ArrayList<ParseNetworkInterceptor>() {{
      add(interceptorA);
      add(interceptorB);
    }};

    Collection<ParseNetworkInterceptor> collectionB = new ArrayList<ParseNetworkInterceptor>() {{
      add(interceptorB);
      add(interceptorA);
    }};

    Parse.Configuration.Builder builder = new Parse.Configuration.Builder(null);

    builder.setNetworkInterceptors(collectionA);
    Parse.Configuration configurationA = builder.build();

    builder.setNetworkInterceptors(collectionB);
    Parse.Configuration configurationB = builder.build();

    assertTrue(collectionsEqual(configurationA.interceptors, collectionA));
    assertTrue(collectionsEqual(configurationB.interceptors, collectionB));
  }

  private static <T> boolean collectionsEqual(Collection<T> a, Collection<T> b) {
    if (a.size() != b.size()) {
      return false;
    }

    Iterator<T> iteratorA = a.iterator();
    Iterator<T> iteratorB = b.iterator();
    for (; iteratorA.hasNext() && iteratorB.hasNext();) {
      T objectA = iteratorA.next();
      T objectB = iteratorB.next();

      if (objectA == null || objectB == null) {
        if (objectA != objectB) {
          return false;
        }
        continue;
      }

      if (!objectA.equals(objectB)) {
        return false;
      }
    }

    if (iteratorA.hasNext() || iteratorB.hasNext()) {
      return false;
    }
    return true;
  }
}

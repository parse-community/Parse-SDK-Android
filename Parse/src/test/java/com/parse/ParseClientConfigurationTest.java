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
}

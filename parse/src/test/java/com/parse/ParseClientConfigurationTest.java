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
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

@RunWith(RobolectricTestRunner.class)
@Config(constants = BuildConfig.class, sdk = TestHelper.ROBOLECTRIC_SDK_VERSION)
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
}

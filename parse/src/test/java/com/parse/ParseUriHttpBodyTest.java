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

import android.net.Uri;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowContentResolver;

@RunWith(RobolectricTestRunner.class)
public class ParseUriHttpBodyTest {
    @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Before
    public void setUp() {
        ParseCorePlugins.getInstance().reset();
        ParsePlugins.reset();
        
        Parse.Configuration configuration =
                new Parse.Configuration.Builder(RuntimeEnvironment.application)
                        .applicationId("test")
                        .server("https://api.parse.com/1")
                        .build();

        ParsePlugins plugins = ParseTestUtils.mockParsePlugins(configuration);
        Parse.initialize(configuration, plugins);
    }

    @After
    public void tearDown() {
        ParseCorePlugins.getInstance().reset();
        ParsePlugins.reset();
        Parse.destroy();
    }

    @Test
    public void testInitializeWithUri() throws IOException {
        byte[] content = {1, 1, 1, 1, 1};
        String contentType = "application/json";
        File file = temporaryFolder.newFile("name");
        ParseFileUtils.writeByteArrayToFile(file, content);
        Uri uri = Uri.fromFile(file);
        
        // Register the Uri with Robolectric's ShadowContentResolver
        ShadowContentResolver shadowContentResolver = 
            Shadows.shadowOf(RuntimeEnvironment.application.getContentResolver());
        shadowContentResolver.registerInputStream(uri, 
            new ByteArrayInputStream(content));
        
        ParseUriHttpBody body = new ParseUriHttpBody(uri, contentType);
        assertArrayEquals(content, ParseIOUtils.toByteArray(body.getContent()));
        assertEquals(contentType, body.getContentType());
        assertEquals(5, body.getContentLength());
    }

    @Test
    public void testWriteTo() throws IOException {
        String content = "content";
        String contentType = "application/json";
        File file = temporaryFolder.newFile("name");
        ParseFileUtils.writeStringToFile(file, content, "UTF-8");
        Uri uri = Uri.fromFile(file);
        
        // Register the Uri with Robolectric's ShadowContentResolver
        ShadowContentResolver shadowContentResolver = 
            Shadows.shadowOf(RuntimeEnvironment.application.getContentResolver());
        shadowContentResolver.registerInputStream(uri, 
            new ByteArrayInputStream(content.getBytes()));
        
        ParseUriHttpBody body = new ParseUriHttpBody(uri, contentType);

        // Check content
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        body.writeTo(output);
        String contentAgain = output.toString();
        assertEquals(content, contentAgain);

        // No need to check whether content input stream is closed since it is a
        // ByteArrayInputStream
    }
}

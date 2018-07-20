/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.skyscreamer.jsonassert.JSONCompareMode;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.skyscreamer.jsonassert.JSONAssert.assertEquals;

public class FileObjectStoreTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Before
    public void setUp() {
        ParseObject.registerSubclass(ParseUser.class);
    }

    @After
    public void tearDown() {
        ParseObject.unregisterSubclass(ParseUser.class);
    }

    @Test
    public void testSetAsync() throws Exception {
        File file = new File(temporaryFolder.getRoot(), "test");

        ParseUser.State state = mock(ParseUser.State.class);
        JSONObject json = new JSONObject();
        json.put("foo", "bar");
        ParseUserCurrentCoder coder = mock(ParseUserCurrentCoder.class);
        when(coder.encode(eq(state), (ParseOperationSet) isNull(), any(PointerEncoder.class)))
                .thenReturn(json);
        FileObjectStore<ParseUser> store = new FileObjectStore<>(ParseUser.class, file, coder);

        ParseUser user = mock(ParseUser.class);
        when(user.getState()).thenReturn(state);
        ParseTaskUtils.wait(store.setAsync(user));

        JSONObject jsonAgain = ParseFileUtils.readFileToJSONObject(file);
        assertEquals(json, jsonAgain, JSONCompareMode.STRICT);
    }

    @Test
    public void testGetAsync() throws Exception {
        File file = new File(temporaryFolder.getRoot(), "test");

        JSONObject json = new JSONObject();
        ParseFileUtils.writeJSONObjectToFile(file, json);

        ParseUser.State.Builder builder = new ParseUser.State.Builder();
        builder.put("foo", "bar");
        ParseUserCurrentCoder coder = mock(ParseUserCurrentCoder.class);
        when(coder.decode(any(ParseUser.State.Builder.class), any(JSONObject.class), any(ParseDecoder.class)))
                .thenReturn(builder);
        FileObjectStore<ParseUser> store = new FileObjectStore<>(ParseUser.class, file, coder);

        ParseUser user = ParseTaskUtils.wait(store.getAsync());
        assertEquals("bar", user.getState().get("foo"));
    }

    @Test
    public void testExistsAsync() throws Exception {
        File file = temporaryFolder.newFile("test");
        FileObjectStore<ParseUser> store = new FileObjectStore<>(ParseUser.class, file, null);
        assertTrue(ParseTaskUtils.wait(store.existsAsync()));

        temporaryFolder.delete();
        assertFalse(ParseTaskUtils.wait(store.existsAsync()));
    }

    @Test
    public void testDeleteAsync() throws Exception {
        File file = temporaryFolder.newFile("test");
        FileObjectStore<ParseUser> store = new FileObjectStore<>(ParseUser.class, file, null);
        assertTrue(file.exists());

        ParseTaskUtils.wait(store.deleteAsync());
        assertFalse(file.exists());
    }
}
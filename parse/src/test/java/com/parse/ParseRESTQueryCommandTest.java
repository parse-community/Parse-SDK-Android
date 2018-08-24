/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import com.parse.http.ParseHttpRequest;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.skyscreamer.jsonassert.JSONCompareMode;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.skyscreamer.jsonassert.JSONAssert.assertEquals;

public class ParseRESTQueryCommandTest {

    @Before
    public void setUp() throws MalformedURLException {
        ParseRESTCommand.server = new URL("https://api.parse.com/1");
    }

    @After
    public void tearDown() {
        ParseRESTCommand.server = null;
    }

    //region testEncode

    @Test
    public void testEncodeWithNoCount() throws Exception {
        ParseQuery.State<ParseObject> state = new ParseQuery.State.Builder<>("TestObject")
                .orderByAscending("orderKey")
                .addCondition("inKey", "$in", Arrays.asList("inValue", "inValueAgain"))
                .selectKeys(Collections.singletonList("selectedKey, selectedKeyAgain"))
                .include("includeKey")
                .setLimit(5)
                .setSkip(6)
                .redirectClassNameForKey("extraKey")
                .setTracingEnabled(true)
                .build();

        Map<String, String> encoded = ParseRESTQueryCommand.encode(state, false);

        assertEquals("orderKey", encoded.get(ParseRESTQueryCommand.KEY_ORDER));
        JSONObject conditionJson = new JSONObject(encoded.get(ParseRESTQueryCommand.KEY_WHERE));
        JSONArray conditionWhereJsonArray = new JSONArray()
                .put("inValue")
                .put("inValueAgain");
        assertEquals(
                conditionWhereJsonArray,
                conditionJson.getJSONObject("inKey").getJSONArray("$in"),
                JSONCompareMode.NON_EXTENSIBLE);
        assertTrue(encoded.get(ParseRESTQueryCommand.KEY_KEYS).contains("selectedKey"));
        assertTrue(encoded.get(ParseRESTQueryCommand.KEY_KEYS).contains("selectedKeyAgain"));
        assertEquals("includeKey", encoded.get(ParseRESTQueryCommand.KEY_INCLUDE));
        assertEquals("5", encoded.get(ParseRESTQueryCommand.KEY_LIMIT));
        assertEquals("6", encoded.get(ParseRESTQueryCommand.KEY_SKIP));
        assertEquals("extraKey", encoded.get("redirectClassNameForKey"));
        assertEquals("1", encoded.get(ParseRESTQueryCommand.KEY_TRACE));
    }

    @Test
    public void testEncodeWithCount() {
        ParseQuery.State<ParseObject> state = new ParseQuery.State.Builder<>("TestObject")
                .setSkip(6)
                .setLimit(3)
                .build();

        Map<String, String> encoded = ParseRESTQueryCommand.encode(state, true);

        // Limit should not be stripped out from count queries
        assertTrue(encoded.containsKey(ParseRESTQueryCommand.KEY_LIMIT));
        assertFalse(encoded.containsKey(ParseRESTQueryCommand.KEY_SKIP));
        assertEquals("1", encoded.get(ParseRESTQueryCommand.KEY_COUNT));
    }

    //endregion

    //region testConstruct

    @Test
    public void testFindCommand() throws Exception {
        ParseQuery.State<ParseObject> state = new ParseQuery.State.Builder<>("TestObject")
                .selectKeys(Arrays.asList("key", "kayAgain"))
                .build();

        ParseRESTQueryCommand command = ParseRESTQueryCommand.findCommand(state, "sessionToken");

        assertEquals("classes/TestObject", command.httpPath);
        assertEquals(ParseHttpRequest.Method.GET, command.method);
        assertEquals("sessionToken", command.getSessionToken());
        Map<String, String> parameters = ParseRESTQueryCommand.encode(state, false);
        JSONObject jsonParameters = (JSONObject) NoObjectsEncoder.get().encode(parameters);
        assertEquals(jsonParameters, command.jsonParameters, JSONCompareMode.NON_EXTENSIBLE);
    }

    @Test
    public void testCountCommand() throws Exception {
        ParseQuery.State<ParseObject> state = new ParseQuery.State.Builder<>("TestObject")
                .selectKeys(Arrays.asList("key", "kayAgain"))
                .build();

        ParseRESTQueryCommand command = ParseRESTQueryCommand.countCommand(state, "sessionToken");

        assertEquals("classes/TestObject", command.httpPath);
        assertEquals(ParseHttpRequest.Method.GET, command.method);
        assertEquals("sessionToken", command.getSessionToken());
        Map<String, String> parameters = ParseRESTQueryCommand.encode(state, true);
        JSONObject jsonParameters = (JSONObject) NoObjectsEncoder.get().encode(parameters);
        assertEquals(jsonParameters, command.jsonParameters, JSONCompareMode.NON_EXTENSIBLE);
    }

    //endregion
}

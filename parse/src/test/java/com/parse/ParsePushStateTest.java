/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.mockito.internal.util.collections.Sets;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ParsePushStateTest {

    //region testDefaults

    @Test(expected = IllegalArgumentException.class)
    public void testDefaultsWithoutData() {
        // We have to set data to a state otherwise it will throw an exception
        JSONObject data = new JSONObject();

        ParsePush.State state = new ParsePush.State.Builder()
                .build();
    }

    @Test
    public void testDefaultsWithData() throws Exception {
        // We have to set data to a state otherwise it will throw an exception
        JSONObject data = new JSONObject();

        ParsePush.State state = new ParsePush.State.Builder()
                .data(data)
                .build();

        assertEquals(null, state.expirationTime());
        assertEquals(null, state.expirationTimeInterval());
        assertEquals(null, state.pushTime());
        assertEquals(null, state.channelSet());
        JSONAssert.assertEquals(data, state.data(), JSONCompareMode.NON_EXTENSIBLE);
        assertEquals(null, state.queryState());
    }

    //endregion

    @Test
    public void testCopy() throws JSONException {
        ParsePush.State state = mock(ParsePush.State.class);
        when(state.expirationTime()).thenReturn(1L);
        when(state.expirationTimeInterval()).thenReturn(2L);
        when(state.pushTime()).thenReturn(3L);
        Set channelSet = Sets.newSet("one", "two");
        when(state.channelSet()).thenReturn(channelSet);
        JSONObject data = new JSONObject();
        data.put("foo", "bar");
        when(state.data()).thenReturn(data);
        ParseQuery.State<ParseInstallation> queryState =
                new ParseQuery.State.Builder<>(ParseInstallation.class).build();
        when(state.queryState()).thenReturn(queryState);

        ParsePush.State copy = new ParsePush.State.Builder(state).build();
        assertSame(1L, copy.expirationTime());
        assertSame(2L, copy.expirationTimeInterval());
        assertSame(3L, copy.pushTime());
        Set channelSetCopy = copy.channelSet();
        assertNotSame(channelSet, channelSetCopy);
        assertTrue(channelSetCopy.size() == 2 && channelSetCopy.contains("one"));
        JSONObject dataCopy = copy.data();
        assertNotSame(data, dataCopy);
        assertEquals("bar", dataCopy.get("foo"));
        ParseQuery.State<ParseInstallation> queryStateCopy = copy.queryState();
        assertNotSame(queryState, queryStateCopy);
        assertEquals("_Installation", queryStateCopy.className());
    }

    //region testExpirationTime

    @Test
    public void testExpirationTimeNullTime() {
        ParsePush.State.Builder builder = new ParsePush.State.Builder();

        ParsePush.State state = builder
                .expirationTime(null)
                .data(new JSONObject())
                .build();

        assertEquals(null, state.expirationTime());
    }

    @Test
    public void testExpirationTimeNormalTime() {
        ParsePush.State.Builder builder = new ParsePush.State.Builder();

        ParsePush.State state = builder
                .expirationTime(100L)
                .data(new JSONObject())
                .build();

        assertEquals(100L, state.expirationTime().longValue());
    }

    //endregion

    //region testExpirationTimeInterval

    @Test
    public void testExpirationTimeIntervalNullInterval() {
        ParsePush.State.Builder builder = new ParsePush.State.Builder();

        ParsePush.State state = builder
                .expirationTimeInterval(null)
                .data(new JSONObject())
                .build();

        assertEquals(null, state.expirationTimeInterval());
    }

    @Test
    public void testExpirationTimeIntervalNormalInterval() {
        ParsePush.State.Builder builder = new ParsePush.State.Builder();

        ParsePush.State state = builder
                .expirationTimeInterval(100L)
                .data(new JSONObject())
                .build();

        assertEquals(100L, state.expirationTimeInterval().longValue());
    }

    //endregion

    //region testPushTime

    @Test
    public void testPushTimeNullTime() {
        ParsePush.State.Builder builder = new ParsePush.State.Builder();

        ParsePush.State state = builder
                .pushTime(null)
                .data(new JSONObject())
                .build();

        assertEquals(null, state.pushTime());
    }

    @Test
    public void testPushTimeNormalTime() {
        ParsePush.State.Builder builder = new ParsePush.State.Builder();

        long time = System.currentTimeMillis() / 1000 + 1000;
        ParsePush.State state = builder
                .pushTime(time)
                .data(new JSONObject())
                .build();

        assertEquals(time, state.pushTime().longValue());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testPushTimeInThePast() {
        ParsePush.State.Builder builder = new ParsePush.State.Builder();

        ParsePush.State state = builder
                .pushTime(System.currentTimeMillis() / 1000 - 1000)
                .data(new JSONObject())
                .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testPushTimeTwoWeeksFromNow() {
        ParsePush.State.Builder builder = new ParsePush.State.Builder();

        ParsePush.State state = builder
                .pushTime(System.currentTimeMillis() / 1000 + 60 * 60 * 24 * 7 * 3)
                .data(new JSONObject())
                .build();
    }

    //endregion

    //region testChannelSet

    @Test(expected = IllegalArgumentException.class)
    public void testChannelSetNullChannelSet() {
        ParsePush.State.Builder builder = new ParsePush.State.Builder();

        ParsePush.State state = builder
                .channelSet(null)
                .data(new JSONObject())
                .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testChannelSetNormalChannelSetWithNullChannel() {
        ParsePush.State.Builder builder = new ParsePush.State.Builder();
        Set<String> channelSet = new HashSet<>();
        channelSet.add(null);

        ParsePush.State state = builder
                .channelSet(channelSet)
                .data(new JSONObject())
                .build();
    }

    @Test
    public void testChannelSetNormalChannelSet() {
        ParsePush.State.Builder builder = new ParsePush.State.Builder();
        Set<String> channelSet = new HashSet<>();
        channelSet.add("foo");
        channelSet.add("bar");

        ParsePush.State state = builder
                .channelSet(channelSet)
                .data(new JSONObject())
                .build();

        assertEquals(2, state.channelSet().size());
        assertTrue(state.channelSet().contains("foo"));
        assertTrue(state.channelSet().contains("bar"));
    }

    @Test
    public void testChannelSetOverwrite() {
        Set<String> channelSet = new HashSet<>();
        channelSet.add("foo");
        Set<String> channelSetAgain = new HashSet<>();
        channelSetAgain.add("bar");

        ParsePush.State state = new ParsePush.State.Builder()
                .channelSet(channelSet)
                .channelSet(channelSetAgain)
                .data(new JSONObject())
                .build();

        assertEquals(1, state.channelSet().size());
        assertTrue(state.channelSet().contains("bar"));
    }

    @Test
    public void testChannelSetDuplicateChannel() {
        final List<String> channelSet = new ArrayList<String>() {{
            add("foo");
            add("foo");
        }};
        ParsePush.State state = new ParsePush.State.Builder()
                .channelSet(channelSet)
                .data(new JSONObject())
                .build();

        assertEquals(1, state.channelSet().size());
        assertTrue(state.channelSet().contains("foo"));
    }

    //endregion

    //region testData

    @Test(expected = IllegalArgumentException.class)
    public void testDataNullData() {
        ParsePush.State.Builder builder = new ParsePush.State.Builder();
        ParsePush.State state = builder
                .data(null)
                .build();
    }

    @Test
    public void testDataNormalData() throws Exception {
        ParsePush.State.Builder builder = new ParsePush.State.Builder();
        JSONObject data = new JSONObject();
        data.put("name", "value");

        ParsePush.State state = builder
                .data(data)
                .build();

        JSONObject dataAgain = state.data();
        assertEquals(1, dataAgain.length());
        assertEquals("value", dataAgain.get("name"));
    }

    //endregion

    //region testQuery

    @Test(expected = IllegalArgumentException.class)
    public void testQueryNullQuery() {
        ParsePush.State.Builder builder = new ParsePush.State.Builder();

        ParsePush.State state = builder
                .query(null)
                .data(new JSONObject())
                .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testQueryNotInstallationQuery() {
        ParsePush.State.Builder builder = new ParsePush.State.Builder();

        ParsePush.State state = builder
                .query(new ParseQuery<ParseInstallation>("test"))
                .data(new JSONObject())
                .build();
    }

    @Test
    public void testQueryNormalQuery() throws Exception {
        ParsePush.State.Builder builder = new ParsePush.State.Builder();
        // Normal query
        ParseQuery<ParseInstallation> query = ParseInstallation.getQuery();
        // Make test ParseQuery state
        ParseQuery.State.Builder<ParseObject> subQueryState =
                new ParseQuery.State.Builder<>("TestObject");
        query.getBuilder()
                .whereEqualTo("foo", "bar")
                .whereMatchesQuery("subquery", subQueryState)
                .setLimit(12)
                .setSkip(34)
                .orderByAscending("foo").addDescendingOrder("bar")
                .include("name")
                .selectKeys(Arrays.asList("name", "blah"))
                .setTracingEnabled(true)
                .redirectClassNameForKey("what");

        ParsePush.State state = builder
                .query(query)
                .data(new JSONObject())
                .build();

        ParseQuery.State queryStateAgain = state.queryState();
        JSONObject queryStateAgainJson = queryStateAgain.toJSON(PointerEncoder.get());
        assertEquals("_Installation", queryStateAgainJson.getString("className"));
        JSONAssert.assertEquals("{" +
                "\"foo\":\"bar\"," +
                "\"subquery\":{\"$inQuery\":{\"className\":\"TestObject\",\"where\":{}}}" +
                "}", queryStateAgainJson.getJSONObject("where"), JSONCompareMode.NON_EXTENSIBLE);
        assertEquals(12, queryStateAgainJson.getInt("limit"));
        assertEquals(34, queryStateAgainJson.getInt("skip"));
        assertEquals("foo,-bar", queryStateAgainJson.getString("order"));
        assertEquals("name", queryStateAgainJson.getString("include"));
        assertEquals("name,blah", queryStateAgainJson.getString("fields"));
        assertEquals(1, queryStateAgainJson.getInt("trace"));
        assertEquals("what", queryStateAgainJson.getString("redirectClassNameForKey"));
    }

    //endregion

    //region testStateImmutable

    @Test
    public void testStateImmutable() throws Exception {
        JSONObject data = new JSONObject();
        data.put("name", "value");
        Set<String> channelSet = new HashSet<>();
        channelSet.add("foo");
        channelSet.add("bar");
        ParsePush.State state = new ParsePush.State.Builder()
                .channelSet(channelSet)
                .data(data)
                .build();

        // Verify channelSet immutable
        Set<String> stateChannelSet = state.channelSet();
        try {
            stateChannelSet.add("test");
            fail("Should throw an exception");
        } catch (UnsupportedOperationException e) {
            // do nothing
        }

        channelSet.add("test");
        assertEquals(2, state.channelSet().size());
        assertTrue(state.channelSet().contains("foo"));
        assertTrue(state.channelSet().contains("bar"));

        // Verify data immutable
        JSONObject stateData = state.data();
        stateData.put("foo", "bar");
        JSONObject stateDataAgain = state.data();
        assertEquals(1, stateDataAgain.length());
        assertEquals("value", stateDataAgain.get("name"));

        // Verify queryState immutable
        // TODO(mengyan) add test after t6941155(Convert mutable parameter to immutable)
    }

    //endregion
}

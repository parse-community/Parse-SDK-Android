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
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(constants = BuildConfig.class, sdk = TestHelper.ROBOLECTRIC_SDK_VERSION)
public class ParseQueryStateTest extends ResetPluginsParseTest {

    @Test
    public void testDefaults() {
        ParseQuery.State.Builder<ParseObject> builder = new ParseQuery.State.Builder<>("TestObject");
        ParseQuery.State<ParseObject> state = builder.build();

        assertEquals("TestObject", state.className());
        assertTrue(state.constraints().isEmpty());
        assertTrue(state.includes().isEmpty());
        assertNull(state.selectedKeys());
        assertEquals(-1, state.limit());
        assertEquals(0, state.skip());
        assertTrue(state.order().isEmpty());
        assertTrue(state.extraOptions().isEmpty());

        assertFalse(state.isTracingEnabled());

        assertEquals(ParseQuery.CachePolicy.IGNORE_CACHE, state.cachePolicy());
        assertEquals(Long.MAX_VALUE, state.maxCacheAge());

        assertFalse(state.isFromLocalDatastore());
        assertNull(state.pinName());
        assertFalse(state.ignoreACLs());
    }

    @Test
    public void testClassName() {
        ParseQuery.State<ParseObject> stateA = new ParseQuery.State.Builder<>("TestObject")
                .build();
        assertEquals("TestObject", stateA.className());

        ParseQuery.State<ParseUser> stateB = new ParseQuery.State.Builder<>(ParseUser.class)
                .build();
        assertEquals("_User", stateB.className());
    }

    @Test
    public void testConstraints() {
        ParseQuery.QueryConstraints constraints;
        ParseQuery.State.Builder<ParseObject> builder = new ParseQuery.State.Builder<>("TestObject");

        constraints = builder
                .whereEqualTo("foo", "bar")
                .whereEqualTo("foo", "baz") // Should overwrite since same key
                .addCondition("people", "$in", Collections.singletonList("stanley")) // Collection
                .addCondition("people", "$in", Collections.singletonList("grantland")) // Collection (overwrite)
                .addCondition("something", "$exists", false) // Object
                .build()
                .constraints();
        assertEquals(3, constraints.size());
        assertEquals("baz", constraints.get("foo"));
        Collection<?> in = ((Collection<?>) ((ParseQuery.KeyConstraints) constraints.get("people")).get("$in"));
        assertEquals(1, in.size());
        assertEquals("grantland", new ArrayList<>(in).get(0));
        assertEquals(false, ((ParseQuery.KeyConstraints) constraints.get("something")).get("$exists"));
    }

    @Test
    public void testConstraintsWithSubqueries() {
        //TODO
    }

    @Test
    public void testParseRelation() {
        //TODO whereRelatedTo, redirectClassNameForKey
    }

    @Test
    public void testOrder() {
        ParseQuery.State<ParseObject> state;
        ParseQuery.State.Builder<ParseObject> builder = new ParseQuery.State.Builder<>("TestObject");

        // Ascending adds
        builder.orderByAscending("foo");
        state = builder.build();
        assertEquals(1, state.order().size());
        assertEquals("foo", state.order().get(0));

        // Descending clears and add
        builder.orderByDescending("foo");
        state = builder.build();
        assertEquals(1, state.order().size());
        assertEquals("-foo", state.order().get(0));

        // Add ascending/descending adds
        builder.addAscendingOrder("bar");
        builder.addDescendingOrder("baz");
        state = builder.build();
        assertEquals(3, state.order().size());
        assertEquals("-foo", state.order().get(0));
        assertEquals("bar", state.order().get(1));
        assertEquals("-baz", state.order().get(2));

        // Ascending clears and adds
        builder.orderByAscending("foo");
        state = builder.build();
        assertEquals(1, state.order().size());
    }

    @Test
    public void testMisc() { // Include, SelectKeys, Limit, Skip
        ParseQuery.State.Builder<ParseObject> builder = new ParseQuery.State.Builder<>("TestObject");

        builder.include("foo").include("bar");
        assertEquals(2, builder.build().includes().size());

        builder.selectKeys(Collections.singletonList("foo")).selectKeys(Arrays.asList("bar", "baz", "qux"));
        assertEquals(4, builder.build().selectedKeys().size());

        builder.setLimit(42);
        assertEquals(42, builder.getLimit());
        assertEquals(42, builder.build().limit());

        builder.setSkip(48);
        assertEquals(48, builder.getSkip());
        assertEquals(48, builder.build().skip());
    }

    @Test
    public void testTrace() {
        assertTrue(new ParseQuery.State.Builder<>("TestObject")
                .setTracingEnabled(true)
                .build()
                .isTracingEnabled());
    }

    @Test
    public void testCachePolicy() {
        //TODO
    }

    //TODO(grantland): Add tests for LDS and throwing for LDS/CachePolicy once we remove OfflineStore
    // global t6942994

    @Test(expected = IllegalStateException.class)
    public void testThrowIfNotLDSAndIgnoreACLs() {
        new ParseQuery.State.Builder<>("TestObject")
                .fromNetwork()
                .ignoreACLs()
                .build();
    }

    //region Or Tests

    @Test
    public void testOr() {
        List<ParseQuery.State.Builder<ParseObject>> subqueries = new ArrayList<>();
        subqueries.add(new ParseQuery.State.Builder<>("TestObject").whereEqualTo("name", "grantland"));
        subqueries.add(new ParseQuery.State.Builder<>("TestObject").whereEqualTo("name", "stanley"));

        ParseQuery.State<ParseObject> state = ParseQuery.State.Builder.or(subqueries).build();
        assertEquals("TestObject", state.className());
        ParseQuery.QueryConstraints constraints = state.constraints();
        assertEquals(1, constraints.size());
        @SuppressWarnings("unchecked")
        List<ParseQuery.QueryConstraints> or =
                (List<ParseQuery.QueryConstraints>) constraints.get("$or");
        assertEquals(2, or.size());
        assertEquals("grantland", or.get(0).get("name"));
        assertEquals("stanley", or.get(1).get("name"));
    }

    @Test
    public void testOrIsMutable() {
        List<ParseQuery.State.Builder<ParseObject>> subqueries = new ArrayList<>();
        ParseQuery.State.Builder<ParseObject> builderA = new ParseQuery.State.Builder<>("TestObject");
        subqueries.add(builderA);
        ParseQuery.State.Builder<ParseObject> builderB = new ParseQuery.State.Builder<>("TestObject");
        subqueries.add(builderB);

        ParseQuery.State.Builder<ParseObject> builder = ParseQuery.State.Builder.or(subqueries);
        // Mutate subquery after `or`
        builderA.whereEqualTo("name", "grantland");

        ParseQuery.State<ParseObject> state = builder.build();
        ParseQuery.QueryConstraints constraints = state.constraints();
        @SuppressWarnings("unchecked")
        List<ParseQuery.QueryConstraints> or =
                (List<ParseQuery.QueryConstraints>) constraints.get("$or");
        assertEquals("grantland", or.get(0).get("name"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testOrThrowsWithEmptyList() {
        ParseQuery.State.Builder.or(new ArrayList<ParseQuery.State.Builder<ParseObject>>()).build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testOrThrowsWithDifferentClassName() {
        List<ParseQuery.State.Builder<ParseObject>> subqueries = new ArrayList<>();
        subqueries.add(new ParseQuery.State.Builder<>("TestObjectA"));
        subqueries.add(new ParseQuery.State.Builder<>("TestObjectB"));
        ParseQuery.State.Builder.or(subqueries).build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testOrThrowsWithLimit() {
        List<ParseQuery.State.Builder<ParseObject>> subqueries = new ArrayList<>();
        subqueries.add(new ParseQuery.State.Builder<>("TestObjectA"));
        subqueries.add(new ParseQuery.State.Builder<>("TestObjectB").setLimit(1));
        ParseQuery.State.Builder.or(subqueries).build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testOrThrowsWithSkip() {
        List<ParseQuery.State.Builder<ParseObject>> subqueries = new ArrayList<>();
        subqueries.add(new ParseQuery.State.Builder<>("TestObjectA"));
        subqueries.add(new ParseQuery.State.Builder<>("TestObjectB").setSkip(1));
        ParseQuery.State.Builder.or(subqueries).build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testOrThrowsWithOrder() {
        List<ParseQuery.State.Builder<ParseObject>> subqueries = new ArrayList<>();
        subqueries.add(new ParseQuery.State.Builder<>("TestObjectA"));
        subqueries.add(new ParseQuery.State.Builder<>("TestObjectB").orderByAscending("blah"));
        ParseQuery.State.Builder.or(subqueries).build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testOrThrowsWithIncludes() {
        List<ParseQuery.State.Builder<ParseObject>> subqueries = new ArrayList<>();
        subqueries.add(new ParseQuery.State.Builder<>("TestObjectA"));
        subqueries.add(new ParseQuery.State.Builder<>("TestObjectB").include("blah"));
        ParseQuery.State.Builder.or(subqueries).build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testOrThrowsWithSelectedKeys() {
        List<ParseQuery.State.Builder<ParseObject>> subqueries = new ArrayList<>();
        subqueries.add(new ParseQuery.State.Builder<>("TestObjectA"));
        subqueries.add(new ParseQuery.State.Builder<>("TestObjectB").selectKeys(Collections.singletonList("blah")));
        ParseQuery.State.Builder.or(subqueries).build();
    }

    //endregion

    @Test
    public void testSubqueryToJSON() throws JSONException {
        ParseEncoder encoder = PointerEncoder.get();
        ParseQuery.State.Builder<ParseObject> builder = new ParseQuery.State.Builder<>("TestObject");

        JSONObject json = builder.build().toJSON(encoder);
        assertEquals("TestObject", json.getString("className"));
        assertEquals("{}", json.getString("where"));
        int count = 0;
        Iterator<String> i = json.keys();
        while (i.hasNext()) {
            i.next();
            count++;
        }
        assertEquals(2, count);

        ParseQuery.State.Builder<ParseObject> subbuilder = new ParseQuery.State.Builder<>("TestObject");

        json = builder
                .whereEqualTo("foo", "bar")
                .whereMatchesQuery("subquery", subbuilder)
                .setLimit(12)
                .setSkip(34)
                .orderByAscending("foo").addDescendingOrder("bar")
                .include("name")
                .selectKeys(Arrays.asList("name", "blah"))
                .setTracingEnabled(true)
                .redirectClassNameForKey("what")
                .build()
                .toJSON(encoder);
        assertEquals("TestObject", json.getString("className"));
        JSONAssert.assertEquals("{" +
                "\"foo\":\"bar\"," +
                "\"subquery\":{\"$inQuery\":{\"className\":\"TestObject\",\"where\":{}}}" +
                "}", json.getJSONObject("where"), JSONCompareMode.NON_EXTENSIBLE);
        assertEquals(12, json.getInt("limit"));
        assertEquals(34, json.getInt("skip"));
        assertEquals("foo,-bar", json.getString("order"));
        assertEquals("name", json.getString("include"));
        assertEquals("name,blah", json.getString("fields"));
        assertEquals(1, json.getInt("trace"));
        assertEquals("what", json.getString("redirectClassNameForKey"));
    }
}

/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import androidx.annotation.NonNull;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

import bolts.Continuation;
import bolts.Task;
import bolts.TaskCompletionSource;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ParseQueryTest {

    private static void verifyCondition(ParseQuery query, String key, Object value) {
        // We generate a state to verify the content of the builder
        ParseQuery.State state = query.getBuilder().build();
        ParseQuery.QueryConstraints queryConstraints = state.constraints();
        assertEquals(value, queryConstraints.get(key));
    }

    private static void verifyCondition(
            ParseQuery query, String key, String conditionKey, Object value) {
        // We generate a state to verify the content of the builder
        ParseQuery.State state = query.getBuilder().build();
        ParseQuery.QueryConstraints queryConstraints = state.constraints();
        ParseQuery.KeyConstraints keyConstraints =
                (ParseQuery.KeyConstraints) queryConstraints.get(key);
        assertEquals(value, keyConstraints.get(conditionKey));
    }

    private static void verifyCondition(
            ParseQuery query, String key, String conditionKey, List values) {
        // We generate a state to verify the content of the builder
        ParseQuery.State state = query.getBuilder().build();
        ParseQuery.QueryConstraints queryConstraints = state.constraints();
        ParseQuery.KeyConstraints keyConstraints =
                (ParseQuery.KeyConstraints) queryConstraints.get(key);
        Collection<String> list = (Collection<String>) keyConstraints.get(conditionKey);
        assertEquals(values.size(), list.size());
        for (Object value : values) {
            assertTrue(list.contains(value));
        }
    }

    private static void verifyCondition(
            ParseQuery query, String key, String conditionKey, Map values) {
        // We generate a state to verify the content of the builder
        ParseQuery.State state = query.getBuilder().build();
        ParseQuery.QueryConstraints queryConstraints = state.constraints();
        ParseQuery.KeyConstraints keyConstraints =
                (ParseQuery.KeyConstraints) queryConstraints.get(key);
        Map map = (Map) keyConstraints.get(conditionKey);
        assertEquals(values.size(), map.size());
        for (Object constraintKey : map.keySet()) {
            assertTrue(values.containsKey(constraintKey));
            assertEquals(map.get(constraintKey), values.get(constraintKey));
        }
    }

    @Before
    public void setUp() {
        ParseTestUtils.setTestParseUser();
        ParseObject.registerSubclass(ParseUser.class);
    }

    @After
    public void tearDown() {
        ParseObject.unregisterSubclass(ParseUser.class);
        ParseCorePlugins.getInstance().reset();
        Parse.disableLocalDatastore();
    }

    @Test
    public void testConstructors() {
        assertEquals("_User", ParseQuery.getQuery(ParseUser.class).getClassName());
        assertEquals("TestObject", ParseQuery.getQuery("TestObject").getClassName());
        assertEquals("_User", new ParseQuery<>(ParseUser.class).getClassName());
        assertEquals("TestObject", new ParseQuery<>("TestObject").getClassName());

        ParseQuery.State.Builder<ParseObject> builder = new ParseQuery.State.Builder<>("TestObject");
        ParseQuery<ParseObject> query = new ParseQuery<>(builder);
        assertEquals("TestObject", query.getClassName());
        assertSame(builder, query.getBuilder());
    }

    @Test
    public void testCopy() {
        ParseQuery<ParseObject> query = new ParseQuery<>("TestObject");
        query.setUser(new ParseUser());
        query.whereEqualTo("foo", "bar");
        ParseQuery.State.Builder<ParseObject> builder = query.getBuilder();
        ParseQuery.State<ParseObject> state = query.getBuilder().build();

        ParseQuery<ParseObject> queryCopy = new ParseQuery<>(query);
        ParseQuery.State.Builder<ParseObject> builderCopy = queryCopy.getBuilder();
        ParseQuery.State<ParseObject> stateCopy = queryCopy.getBuilder().build();

        assertNotSame(query, queryCopy);
        assertSame(query.getUserAsync(state).getResult(), queryCopy.getUserAsync(stateCopy).getResult());

        assertNotSame(builder, builderCopy);
        assertSame(state.constraints().get("foo"), stateCopy.constraints().get("foo"));
    }

    // ParseUser#setUser is for tests only
    @Test
    public void testSetUser() throws ParseException {
        ParseQuery<ParseObject> query = ParseQuery.getQuery("TestObject");

        ParseUser user = new ParseUser();
        query.setUser(user);
        assertSame(user, ParseTaskUtils.wait(query.getUserAsync(query.getBuilder().build())));

        // TODO(grantland): Test that it gets the current user

        Parse.enableLocalDatastore(null);
        query.fromLocalDatastore()
                .ignoreACLs();
        assertNull(ParseTaskUtils.wait(query.getUserAsync(query.getBuilder().build())));
    }

    @Test
    public void testMultipleQueries() {
        TestQueryController controller1 = new TestQueryController();
        TestQueryController controller2 = new TestQueryController();

        TaskCompletionSource<Void> tcs1 = new TaskCompletionSource<>();
        TaskCompletionSource<Void> tcs2 = new TaskCompletionSource<>();
        controller1.await(tcs1.getTask());
        controller2.await(tcs2.getTask());

        ParseQuery<ParseObject> query = ParseQuery.getQuery("TestObject");
        query.setUser(new ParseUser());

        ParseCorePlugins.getInstance().registerQueryController(controller1);
        query.findInBackground();
        assertTrue(query.isRunning());

        ParseCorePlugins.getInstance().reset();
        ParseCorePlugins.getInstance().registerQueryController(controller2);
        query.countInBackground();
        assertTrue(query.isRunning());

        // Stop the first operation.
        tcs1.setResult(null);
        assertTrue(query.isRunning());

        // Stop the second.
        tcs2.setResult(null);
        assertFalse(query.isRunning());
    }

    @Test
    public void testMultipleQueriesWithInflightChanges() throws ParseException {
        Parse.enableLocalDatastore(null);
        TestQueryController controller = new TestQueryController();
        TaskCompletionSource<Void> tcs = new TaskCompletionSource<>();
        controller.await(tcs.getTask());

        ParseQuery<ParseObject> query = ParseQuery.getQuery("TestObject");
        query.setUser(new ParseUser());

        ParseCorePlugins.getInstance().registerQueryController(controller);
        List<Task<Void>> tasks = Arrays.asList(
                query.fromNetwork().findInBackground().makeVoid(),
                query.fromLocalDatastore().findInBackground().makeVoid(),
                query.setLimit(10).findInBackground().makeVoid(),
                query.whereEqualTo("key", "value").countInBackground().makeVoid());
        assertTrue(query.isRunning());
        tcs.trySetResult(null);
        ParseTaskUtils.wait(Task.whenAll(tasks));
        assertFalse(query.isRunning());
    }

    @Test
    public void testCountLimitReset() {
        // Mock CacheQueryController
        ParseQueryController controller = mock(CacheQueryController.class);
        ParseCorePlugins.getInstance().registerQueryController(controller);
        when(controller.countAsync(
                any(ParseQuery.State.class),
                any(ParseUser.class),
                any(Task.class))).thenReturn(Task.forResult(0));

        final ParseQuery<ParseObject> query = ParseQuery.getQuery("TestObject");

        query.countInBackground();
        assertEquals(-1, query.getLimit());
    }

    @Test
    public void testCountWithCallbackLimitReset() {
        // Mock CacheQueryController
        CacheQueryController controller = mock(CacheQueryController.class);
        ParseCorePlugins.getInstance().registerQueryController(controller);
        when(controller.countAsync(
                any(ParseQuery.State.class),
                any(ParseUser.class),
                any(Task.class))).thenReturn(Task.forResult(0));

        final ParseQuery<ParseObject> query = ParseQuery.getQuery("TestObject");

        query.countInBackground(null);
        assertEquals(-1, query.getLimit());
    }

    // TODO(grantland): Add CACHE_THEN_NETWORK tests (find, count, getFirst, get)

    // TODO(grantland): Add cache tests (hasCachedResult, clearCachedResult, clearAllCachedResults)

    // TODO(grantland): Add ParseQuery -> ParseQuery.State.Builder calls

    //region testConditions

    @Test
    public void testCountLimit() {
        CacheQueryController controller = mock(CacheQueryController.class);
        ParseCorePlugins.getInstance().registerQueryController(controller);
        when(controller.countAsync(
                any(ParseQuery.State.class),
                any(ParseUser.class),
                any(Task.class))).thenReturn(Task.forResult(0));

        ArgumentCaptor<ParseQuery.State> state = ArgumentCaptor.forClass(ParseQuery.State.class);

        final ParseQuery<ParseObject> query = ParseQuery.getQuery("TestObject");
        query.countInBackground();
        verify(controller, times(1)).countAsync(state.capture(), any(ParseUser.class), any(Task.class));
        assertEquals(0, state.getValue().limit());
    }

    @Test
    public void testCountWithCallbackLimit() {
        CacheQueryController controller = mock(CacheQueryController.class);
        ParseCorePlugins.getInstance().registerQueryController(controller);
        when(controller.countAsync(
                any(ParseQuery.State.class),
                any(ParseUser.class),
                any(Task.class))).thenReturn(Task.forResult(0));

        ArgumentCaptor<ParseQuery.State> state = ArgumentCaptor.forClass(ParseQuery.State.class);

        final ParseQuery<ParseObject> query = ParseQuery.getQuery("TestObject");
        query.countInBackground(null);
        verify(controller, times(1)).countAsync(state.capture(), any(ParseUser.class), any(Task.class));
        assertEquals(0, state.getValue().limit());
    }

    @Test
    public void testIsRunning() {
        TestQueryController controller = new TestQueryController();
        ParseCorePlugins.getInstance().registerQueryController(controller);
        TaskCompletionSource<Void> tcs = new TaskCompletionSource<>();
        controller.await(tcs.getTask());

        ParseQuery<ParseObject> query = ParseQuery.getQuery("TestObject");
        query.setUser(new ParseUser());
        assertFalse(query.isRunning());
        query.findInBackground();
        assertTrue(query.isRunning());
        tcs.setResult(null);
        assertFalse(query.isRunning());
        // Run another
        tcs = new TaskCompletionSource<>();
        controller.await(tcs.getTask());
        query.findInBackground();
        assertTrue(query.isRunning());
        query.cancel();
        assertFalse(query.isRunning());
    }

    @Test
    public void testQueryCancellation() throws ParseException {
        TestQueryController controller = new TestQueryController();
        ParseCorePlugins.getInstance().registerQueryController(controller);

        TaskCompletionSource<Void> tcs = new TaskCompletionSource();
        controller.await(tcs.getTask());

        ParseQuery<ParseObject> query = ParseQuery.getQuery("TestObject");
        query.setUser(new ParseUser());
        Task<Void> task = query.findInBackground().makeVoid();

        query.cancel();
        tcs.setResult(null);
        try {
            ParseTaskUtils.wait(task);
        } catch (RuntimeException e) {
            assertThat(e.getCause(), instanceOf(CancellationException.class));
        }

        // Should succeed
        task = query.findInBackground().makeVoid();
        ParseTaskUtils.wait(task);
    }

    @Test
    public void testCachePolicy() {
        ParseQuery<ParseObject> query = new ParseQuery<>("Test");
        query.setCachePolicy(ParseQuery.CachePolicy.CACHE_ELSE_NETWORK);

        assertEquals(ParseQuery.CachePolicy.CACHE_ELSE_NETWORK, query.getCachePolicy());
    }

    @Test
    public void testFromNetwork() {
        ParseQuery<ParseObject> query = new ParseQuery<>("Test");
        Parse.enableLocalDatastore(null);
        query.fromNetwork();

        assertTrue(query.isFromNetwork());
    }

    @Test
    public void testFromPin() {
        ParseQuery<ParseObject> query = new ParseQuery<>("Test");
        Parse.enableLocalDatastore(null);
        query.fromPin();

        // We generate a state to verify the content of the builder
        ParseQuery.State state = query.getBuilder().build();
        assertTrue(state.isFromLocalDatastore());
        assertEquals(ParseObject.DEFAULT_PIN, state.pinName());
    }

    @Test
    public void testMaxCacheAge() {
        ParseQuery<ParseObject> query = new ParseQuery<>("Test");
        query.setMaxCacheAge(10);

        assertEquals(10, query.getMaxCacheAge());
    }

    @Test
    public void testWhereNotEqualTo() {
        ParseQuery<ParseObject> query = new ParseQuery<>("Test");

        query.whereNotEqualTo("key", "value");

        verifyCondition(query, "key", "$ne", "value");
    }

    @Test
    public void testWhereGreaterThan() {
        ParseQuery<ParseObject> query = new ParseQuery<>("Test");

        query.whereGreaterThan("key", "value");

        verifyCondition(query, "key", "$gt", "value");
    }

    @Test
    public void testWhereLessThanOrEqualTo() {
        ParseQuery<ParseObject> query = new ParseQuery<>("Test");

        query.whereLessThanOrEqualTo("key", "value");

        verifyCondition(query, "key", "$lte", "value");
    }

    @Test
    public void testWhereGreaterThanOrEqualTo() {
        ParseQuery<ParseObject> query = new ParseQuery<>("Test");

        query.whereGreaterThanOrEqualTo("key", "value");

        verifyCondition(query, "key", "$gte", "value");
    }

    @Test
    public void testWhereContainedIn() {
        ParseQuery<ParseObject> query = new ParseQuery<>("Test");
        List<String> values = Arrays.asList("value", "valueAgain");

        query.whereContainedIn("key", values);

        verifyCondition(query, "key", "$in", values);
    }

    @Test
    public void testWhereFullText() {
        ParseQuery<ParseObject> query = new ParseQuery<>("Test");
        String text = "TestString";
        query.whereFullText("key", text);

        // We generate a state to verify the content of the builder
        ParseQuery.State state = query.getBuilder().build();
        ParseQuery.QueryConstraints queryConstraints = state.constraints();
        ParseQuery.KeyConstraints keyConstraints = (ParseQuery.KeyConstraints) queryConstraints.get("key");
        Map searchDictionary = (Map) keyConstraints.get("$text");
        Map termDictionary = (Map) searchDictionary.get("$search");
        String value = (String) termDictionary.get("$term");
        assertEquals(value, text);
    }

    @Test
    public void testWhereContainsAll() {
        ParseQuery<ParseObject> query = new ParseQuery<>("Test");
        List<String> values = Arrays.asList("value", "valueAgain");

        query.whereContainsAll("key", values);

        verifyCondition(query, "key", "$all", values);
    }

    @Test
    public void testWhereContainsAllStartingWith() {
        ParseQuery<ParseObject> query = new ParseQuery<>("Test");
        String value = "value";
        String valueAgain = "valueAgain";
        List<String> values = Arrays.asList(value, valueAgain);

        ParseQuery.KeyConstraints valueConverted = new ParseQuery.KeyConstraints();
        valueConverted.put("$regex", buildStartsWithPattern(value));
        ParseQuery.KeyConstraints valueAgainConverted = new ParseQuery.KeyConstraints();
        valueAgainConverted.put("$regex", buildStartsWithPattern(valueAgain));
        List<ParseQuery.KeyConstraints> valuesConverted =
                Arrays.asList(valueConverted, valueAgainConverted);

        query.whereContainsAllStartsWith("key", values);

        verifyCondition(query, "key", "$all", valuesConverted);
    }

    @Test
    public void testWhereNotContainedIn() {
        ParseQuery<ParseObject> query = new ParseQuery<>("Test");
        List<String> values = Arrays.asList("value", "valueAgain");

        query.whereNotContainedIn("key", values);

        verifyCondition(query, "key", "$nin", values);
    }

    @Test
    public void testWhereMatches() {
        ParseQuery<ParseObject> query = new ParseQuery<>("Test");

        query.whereMatches("key", "regex");

        verifyCondition(query, "key", "$regex", "regex");
    }

    @Test
    public void testWhereMatchesWithModifiers() {
        ParseQuery<ParseObject> query = new ParseQuery<>("Test");

        query.whereMatches("key", "regex", "modifiers");

        verifyCondition(query, "key", "$regex", "regex");
        verifyCondition(query, "key", "$options", "modifiers");
    }

    @Test
    public void testWhereStartsWith() {
        ParseQuery<ParseObject> query = new ParseQuery<>("Test");

        String value = "prefix";
        query.whereStartsWith("key", value);

        verifyCondition(query, "key", "$regex", buildStartsWithPattern(value));
    }

    @Test
    public void testWhereEndsWith() {
        ParseQuery<ParseObject> query = new ParseQuery<>("Test");

        String value = "suffix";
        query.whereEndsWith("key", value);

        verifyCondition(query, "key", "$regex", Pattern.quote(value) + "$");
    }

    @Test
    public void testWhereExists() {
        ParseQuery<ParseObject> query = new ParseQuery<>("Test");

        query.whereExists("key");

        verifyCondition(query, "key", "$exists", true);
    }

    @Test
    public void testWhereDoesNotExist() {
        ParseQuery<ParseObject> query = new ParseQuery<>("Test");

        query.whereDoesNotExist("key");

        verifyCondition(query, "key", "$exists", false);
    }

    @Test
    public void testWhereContains() {
        ParseQuery<ParseObject> query = new ParseQuery<>("Test");

        String value = "value";
        query.whereContains("key", value);

        verifyCondition(query, "key", "$regex", Pattern.quote(value));
    }

    @Test
    public void testWhereMatchesQuery() {
        ParseQuery<ParseObject> query = new ParseQuery<>("Test");
        ParseQuery<ParseObject> conditionQuery = new ParseQuery<>("Test");
        conditionQuery.whereExists("keyAgain");

        query.whereMatchesQuery("key", conditionQuery);

        verifyCondition(query, "key", "$inQuery", conditionQuery.getBuilder());
    }

    @Test
    public void testWhereDoesNotMatchQuery() {
        ParseQuery<ParseObject> query = new ParseQuery<>("Test");
        ParseQuery<ParseObject> conditionQuery = new ParseQuery<>("Test");
        conditionQuery.whereExists("keyAgain");

        query.whereDoesNotMatchQuery("key", conditionQuery);

        verifyCondition(query, "key", "$notInQuery", conditionQuery.getBuilder());
    }

    @Test
    public void testWhereMatchesKeyInQuery() {
        ParseQuery<ParseObject> query = new ParseQuery<>("Test");
        ParseQuery<ParseObject> conditionQuery = new ParseQuery<>("Test");
        conditionQuery.whereExists("keyAgain");

        query.whereMatchesKeyInQuery("key", "keyAgain", conditionQuery);

        Map<String, Object> conditions = new HashMap<>();
        conditions.put("key", "keyAgain");
        conditions.put("query", conditionQuery.getBuilder());
        verifyCondition(query, "key", "$select", conditions);
    }

    @Test
    public void testWhereDoesNotMatchKeyInQuery() {
        ParseQuery<ParseObject> query = new ParseQuery<>("Test");
        ParseQuery<ParseObject> conditionQuery = new ParseQuery<>("Test");
        conditionQuery.whereExists("keyAgain");

        query.whereDoesNotMatchKeyInQuery("key", "keyAgain", conditionQuery);

        Map<String, Object> conditions = new HashMap<>();
        conditions.put("key", "keyAgain");
        conditions.put("query", conditionQuery.getBuilder());
        verifyCondition(query, "key", "$dontSelect", conditions);
    }

    @Test
    public void testWhereNear() {
        ParseQuery<ParseObject> query = new ParseQuery<>("Test");
        ParseGeoPoint point = new ParseGeoPoint(10, 10);

        query.whereNear("key", point);

        verifyCondition(query, "key", "$nearSphere", point);
    }

    @Test
    public void testWhereWithinGeoBox() {
        ParseQuery<ParseObject> query = new ParseQuery<>("Test");
        ParseGeoPoint point = new ParseGeoPoint(10, 10);
        ParseGeoPoint pointAgain = new ParseGeoPoint(20, 20);

        query.whereWithinGeoBox("key", point, pointAgain);

        // We generate a state to verify the content of the builder
        ParseQuery.State state = query.getBuilder().build();
        ParseQuery.QueryConstraints queryConstraints = state.constraints();
        ParseQuery.KeyConstraints keyConstraints =
                (ParseQuery.KeyConstraints) queryConstraints.get("key");
        Map map = (Map) keyConstraints.get("$within");
        List<Object> list = (List<Object>) map.get("$box");
        assertEquals(2, list.size());
        assertTrue(list.contains(point));
        assertTrue(list.contains(pointAgain));
    }

    @Test
    public void testWhereWithinPolygon() {
        ParseQuery<ParseObject> query = new ParseQuery<>("Test");
        ParseGeoPoint point1 = new ParseGeoPoint(10, 10);
        ParseGeoPoint point2 = new ParseGeoPoint(20, 20);
        ParseGeoPoint point3 = new ParseGeoPoint(30, 30);

        List<ParseGeoPoint> points = Arrays.asList(point1, point2, point3);
        query.whereWithinPolygon("key", points);

        // We generate a state to verify the content of the builder
        ParseQuery.State state = query.getBuilder().build();
        ParseQuery.QueryConstraints queryConstraints = state.constraints();
        ParseQuery.KeyConstraints keyConstraints = (ParseQuery.KeyConstraints) queryConstraints.get("key");
        Map map = (Map) keyConstraints.get("$geoWithin");
        List<Object> list = (List<Object>) map.get("$polygon");
        assertEquals(3, list.size());
        assertTrue(list.contains(point1));
        assertTrue(list.contains(point2));
        assertTrue(list.contains(point3));
    }

    @Test
    public void testWhereWithinPolygonWithPolygon() {
        ParseQuery<ParseObject> query = new ParseQuery<>("Test");
        ParseGeoPoint point1 = new ParseGeoPoint(10, 10);
        ParseGeoPoint point2 = new ParseGeoPoint(20, 20);
        ParseGeoPoint point3 = new ParseGeoPoint(30, 30);

        List<ParseGeoPoint> points = Arrays.asList(point1, point2, point3);
        query.whereWithinPolygon("key", new ParsePolygon(points));

        // We generate a state to verify the content of the builder
        ParseQuery.State state = query.getBuilder().build();
        ParseQuery.QueryConstraints queryConstraints = state.constraints();
        ParseQuery.KeyConstraints keyConstraints = (ParseQuery.KeyConstraints) queryConstraints.get("key");
        Map map = (Map) keyConstraints.get("$geoWithin");
        List<Object> list = (List<Object>) map.get("$polygon");
        assertEquals(3, list.size());
        assertTrue(list.contains(point1));
        assertTrue(list.contains(point2));
        assertTrue(list.contains(point3));
    }

    @Test
    public void testWherePolygonContains() {
        ParseQuery<ParseObject> query = new ParseQuery<>("Test");
        ParseGeoPoint point = new ParseGeoPoint(10, 10);

        query.wherePolygonContains("key", point);

        // We generate a state to verify the content of the builder
        ParseQuery.State state = query.getBuilder().build();
        ParseQuery.QueryConstraints queryConstraints = state.constraints();
        ParseQuery.KeyConstraints keyConstraints =
                (ParseQuery.KeyConstraints) queryConstraints.get("key");
        Map map = (Map) keyConstraints.get("$geoIntersects");
        ParseGeoPoint geoPoint = (ParseGeoPoint) map.get("$point");
        assertEquals(geoPoint, point);
    }

    @Test
    public void testWhereWithinRadians() {
        ParseQuery<ParseObject> query = new ParseQuery<>("Test");
        ParseGeoPoint point = new ParseGeoPoint(10, 10);

        query.whereWithinRadians("key", point, 100.0);

        verifyCondition(query, "key", "$nearSphere", point);
        verifyCondition(query, "key", "$maxDistance", 100.0);
    }

    // TODO(mengyan): Add testOr illegal cases unit test

    @Test
    public void testWhereWithinMiles() {
        ParseQuery<ParseObject> query = new ParseQuery<>("Test");
        ParseGeoPoint point = new ParseGeoPoint(10, 10);

        query.whereWithinMiles("key", point, 100.0);

        verifyCondition(query, "key", "$nearSphere", point);
        verifyCondition(query, "key", "$maxDistance", 100.0 / ParseGeoPoint.EARTH_MEAN_RADIUS_MILE);
    }

    @Test
    public void testWhereWithinKilometers() {
        ParseQuery<ParseObject> query = new ParseQuery<>("Test");
        ParseGeoPoint point = new ParseGeoPoint(10, 10);

        query.whereWithinKilometers("key", point, 100.0);

        verifyCondition(query, "key", "$nearSphere", point);
        verifyCondition(query, "key", "$maxDistance", 100.0 / ParseGeoPoint.EARTH_MEAN_RADIUS_KM);
    }

    @Test
    public void testClear() {
        ParseQuery<ParseObject> query = new ParseQuery<>("Test");
        query.whereEqualTo("key", "value");
        query.whereEqualTo("otherKey", "otherValue");
        verifyCondition(query, "key", "value");
        verifyCondition(query, "otherKey", "otherValue");
        query.clear("key");
        verifyCondition(query, "key", null);
        verifyCondition(query, "otherKey", "otherValue"); // still.
    }

    @Test
    public void testOr() {
        ParseQuery<ParseObject> query = new ParseQuery<>("Test");
        query.whereEqualTo("key", "value");
        ParseQuery<ParseObject> queryAgain = new ParseQuery<>("Test");
        queryAgain.whereEqualTo("keyAgain", "valueAgain");
        List<ParseQuery<ParseObject>> queries = Arrays.asList(query, queryAgain);
        ParseQuery<ParseObject> combinedQuery = ParseQuery.or(queries);

        // We generate a state to verify the content of the builder
        ParseQuery.State state = combinedQuery.getBuilder().build();
        ParseQuery.QueryConstraints combinedQueryConstraints = state.constraints();
        List list = (List) combinedQueryConstraints.get("$or");
        assertEquals(2, list.size());
        // Verify query constraint
        ParseQuery.QueryConstraints queryConstraintsFromCombinedQuery =
                (ParseQuery.QueryConstraints) list.get(0);
        assertEquals(1, queryConstraintsFromCombinedQuery.size());
        assertEquals("value", queryConstraintsFromCombinedQuery.get("key"));
        // Verify queryAgain constraint
        ParseQuery.QueryConstraints queryAgainConstraintsFromCombinedQuery =
                (ParseQuery.QueryConstraints) list.get(1);
        assertEquals(1, queryAgainConstraintsFromCombinedQuery.size());
        assertEquals("valueAgain", queryAgainConstraintsFromCombinedQuery.get("keyAgain"));
    }

    @Test
    public void testInclude() {
        ParseQuery<ParseObject> query = new ParseQuery<>("Test");

        query.include("key");

        // We generate a state to verify the content of the builder
        ParseQuery.State state = query.getBuilder().build();
        assertEquals(1, state.includes().size());
        assertTrue(state.includes().contains("key"));
    }

    @Test
    public void testSelectKeys() {
        ParseQuery<ParseObject> query = new ParseQuery<>("Test");

        query.selectKeys(Arrays.asList("key", "keyAgain"));

        // We generate a state to verify the content of the builder
        ParseQuery.State state = query.getBuilder().build();
        assertEquals(2, state.selectedKeys().size());
        assertTrue(state.selectedKeys().contains("key"));
        assertTrue(state.selectedKeys().contains("keyAgain"));
    }

    @Test
    public void testAddAscendingOrder() {
        ParseQuery<ParseObject> query = new ParseQuery<>("Test");

        query.addAscendingOrder("key");

        // We generate a state to verify the content of the builder
        ParseQuery.State state = query.getBuilder().build();
        assertEquals(1, state.order().size());
        assertTrue(state.order().contains("key"));
    }

    @Test
    public void testAddDescendingOrder() {
        ParseQuery<ParseObject> query = new ParseQuery<>("Test");

        query.addDescendingOrder("key");

        // We generate a state to verify the content of the builder
        ParseQuery.State state = query.getBuilder().build();
        assertEquals(1, state.order().size());
        assertTrue(state.order().contains(String.format("-%s", "key")));
    }

    @Test
    public void testOrderByAscending() {
        ParseQuery<ParseObject> query = new ParseQuery<>("Test");

        query.orderByAscending("key");
        query.orderByAscending("keyAgain");

        // We generate a state to verify the content of the builder
        ParseQuery.State state = query.getBuilder().build();
        assertEquals(1, state.order().size());
        assertTrue(state.order().contains("keyAgain"));
    }

    @Test
    public void testOrderByDescending() {
        ParseQuery<ParseObject> query = new ParseQuery<>("Test");

        query.orderByDescending("key");
        query.orderByDescending("keyAgain");

        // We generate a state to verify the content of the builder
        ParseQuery.State state = query.getBuilder().build();
        assertEquals(1, state.order().size());
        assertTrue(state.order().contains(String.format("-%s", "keyAgain")));
    }

    @Test
    public void testLimit() {
        ParseQuery<ParseObject> query = new ParseQuery<>("Test");

        query.setLimit(5);

        assertEquals(5, query.getLimit());
    }

    @Test
    public void testSkip() {
        ParseQuery<ParseObject> query = new ParseQuery<>("Test");

        query.setSkip(5);

        assertEquals(5, query.getSkip());
    }

    //endregion

    @NonNull
    private String buildStartsWithPattern(String value) {
        return "^" + Pattern.quote(value);
    }

    /**
     * A {@link ParseQueryController} used for testing.
     */
    private static class TestQueryController implements ParseQueryController {

        private Task<Void> toAwait = Task.forResult(null);

        public Task<Void> await(final Task<Void> task) {
            toAwait = toAwait.continueWithTask(new Continuation<Void, Task<Void>>() {
                @Override
                public Task<Void> then(Task<Void> ignored) {
                    return task;
                }
            });
            return toAwait;
        }

        @Override
        public <T extends ParseObject> Task<List<T>> findAsync(ParseQuery.State<T> state,
                                                               ParseUser user, Task<Void> cancellationToken) {
            final AtomicBoolean cancelled = new AtomicBoolean(false);
            cancellationToken.continueWith(new Continuation<Void, Void>() {
                @Override
                public Void then(Task<Void> task) {
                    cancelled.set(true);
                    return null;
                }
            });
            return await(Task.<Void>forResult(null).continueWithTask(new Continuation<Void, Task<Void>>() {
                @Override
                public Task<Void> then(Task<Void> task) {
                    if (cancelled.get()) {
                        return Task.cancelled();
                    }
                    return task;
                }
            })).cast();
        }

        @Override
        public <T extends ParseObject> Task<Integer> countAsync(ParseQuery.State<T> state,
                                                                ParseUser user, Task<Void> cancellationToken) {
            final AtomicBoolean cancelled = new AtomicBoolean(false);
            cancellationToken.continueWith(new Continuation<Void, Void>() {
                @Override
                public Void then(Task<Void> task) {
                    cancelled.set(true);
                    return null;
                }
            });
            return await(Task.<Void>forResult(null).continueWithTask(new Continuation<Void, Task<Void>>() {
                @Override
                public Task<Void> then(Task<Void> task) {
                    if (cancelled.get()) {
                        return Task.cancelled();
                    }
                    return task;
                }
            })).cast();
        }

        @Override
        public <T extends ParseObject> Task<T> getFirstAsync(ParseQuery.State<T> state,
                                                             ParseUser user, Task<Void> cancellationToken) {
            final AtomicBoolean cancelled = new AtomicBoolean(false);
            cancellationToken.continueWith(new Continuation<Void, Void>() {
                @Override
                public Void then(Task<Void> task) {
                    cancelled.set(true);
                    return null;
                }
            });
            return await(Task.<Void>forResult(null).continueWithTask(new Continuation<Void, Task<Void>>() {
                @Override
                public Task<Void> then(Task<Void> task) {
                    if (cancelled.get()) {
                        return Task.cancelled();
                    }
                    return task;
                }
            })).cast();
        }
    }
}

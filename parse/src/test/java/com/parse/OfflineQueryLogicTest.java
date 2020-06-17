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

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.parse.boltsinternal.Task;

import static com.parse.ParseMatchers.hasParseErrorCode;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for OfflineQueryLogic.
 */
public class OfflineQueryLogicTest {

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private static <T extends ParseObject> boolean matches(
            OfflineQueryLogic logic, ParseQuery.State<T> query, T object) throws ParseException {
        return matches(logic, query, object, null);
    }

    //region hasReadAccess

    private static <T extends ParseObject> boolean matches(
            OfflineQueryLogic logic, ParseQuery.State<T> query, T object, ParseUser user)
            throws ParseException {
        Task<Boolean> task = logic.createMatcher(query, user).matchesAsync(object, null);
        return ParseTaskUtils.wait(task);
    }

    private static List<ParseObject> generateParseObjects(
            String key, Object[] values) {
        List<ParseObject> objects = new ArrayList<>();
        int i = 0;
        for (Object value : values) {
            ParseObject object = ParseObject.create("TestObject");
            object.put("id", i);
            if (value != null) {
                object.put(key, value);
            }
            objects.add(object);

            i++;
        }
        return objects;
    }

    @After
    public void tearDown() {
        ParseCorePlugins.getInstance().reset();
    }

    @Test
    public void testHasReadAccessWithSameObject() {
        ParseUser user = mock(ParseUser.class);

        assertTrue(OfflineQueryLogic.hasReadAccess(user, user));
        verify(user, never()).getACL();
    }

    @Test
    public void testHasReadAccessWithNoACL() {
        ParseObject object = mock(ParseObject.class);
        when(object.getACL()).thenReturn(null);

        assertTrue(OfflineQueryLogic.hasReadAccess(null, object));
    }

    //endregion

    //region hasWriteAccess

    @Test
    public void testHasReadAccessWithPublicReadAccess() {
        ParseACL acl = mock(ParseACL.class);
        when(acl.getPublicReadAccess()).thenReturn(true);

        ParseObject object = mock(ParseObject.class);
        when(object.getACL()).thenReturn(acl);

        assertTrue(OfflineQueryLogic.hasReadAccess(null, object));
    }

    @Test
    public void testHasReadAccessWithReadAccess() {
        ParseUser user = mock(ParseUser.class);
        when(user.getObjectId()).thenReturn("test");

        ParseACL acl = mock(ParseACL.class);
        when(acl.getReadAccess(user)).thenReturn(true);

        ParseObject object = mock(ParseObject.class);
        when(object.getACL()).thenReturn(acl);

        assertTrue(OfflineQueryLogic.hasReadAccess(user, object));
    }

    @Test
    public void testHasReadAccessWithNoReadAccess() {
        ParseACL acl = mock(ParseACL.class);
        when(acl.getPublicReadAccess()).thenReturn(false);
        when(acl.getReadAccess(any(ParseUser.class))).thenReturn(false);

        ParseObject object = mock(ParseObject.class);
        when(object.getACL()).thenReturn(acl);

        assertFalse(OfflineQueryLogic.hasReadAccess(null, object));
    }

    @Test
    public void testHasWriteAccessWithSameObject() {
        ParseUser user = mock(ParseUser.class);

        assertTrue(OfflineQueryLogic.hasWriteAccess(user, user));
        verify(user, never()).getACL();
    }

    @Test
    public void testHasWriteAccessWithNoACL() {
        ParseObject object = mock(ParseObject.class);
        when(object.getACL()).thenReturn(null);

        assertTrue(OfflineQueryLogic.hasWriteAccess(null, object));
    }

    //endregion

    //region createMatcher

    @Test
    public void testHasWriteAccessWithPublicWriteAccess() {
        ParseACL acl = mock(ParseACL.class);
        when(acl.getPublicWriteAccess()).thenReturn(true);

        ParseObject object = mock(ParseObject.class);
        when(object.getACL()).thenReturn(acl);

        assertTrue(OfflineQueryLogic.hasWriteAccess(null, object));
    }

    @Test
    public void testHasWriteAccessWithWriteAccess() {
        ParseUser user = mock(ParseUser.class);

        ParseACL acl = mock(ParseACL.class);
        when(acl.getWriteAccess(user)).thenReturn(true);

        ParseObject object = mock(ParseObject.class);
        when(object.getACL()).thenReturn(acl);

        assertTrue(OfflineQueryLogic.hasWriteAccess(user, object));
    }

    @Test
    public void testHasWriteAccessWithNoWriteAccess() {
        ParseACL acl = mock(ParseACL.class);
        when(acl.getPublicReadAccess()).thenReturn(false);

        ParseObject object = mock(ParseObject.class);
        when(object.getACL()).thenReturn(acl);

        assertFalse(OfflineQueryLogic.hasWriteAccess(null, object));
    }

    @Test
    public void testMatcherWithNoReadAccess() throws ParseException {
        OfflineQueryLogic logic = new OfflineQueryLogic(null);

        ParseQuery.State<ParseObject> query = new ParseQuery.State.Builder<>("TestObject")
                .build();

        ParseACL acl = new ParseACL();
        acl.setPublicReadAccess(false);
        ParseObject object = new ParseObject("TestObject");
        object.setACL(acl);

        ParseUser user = mock(ParseUser.class);
        when(user.getObjectId()).thenReturn("test");

        assertFalse(matches(logic, query, object, user));
    }

    // TODO(grantland): testRelationMatcher()

    //endregion

    //region matchesEquals

    @Test
    public void testSimpleMatcher() throws ParseException {
        OfflineQueryLogic logic = new OfflineQueryLogic(null);

        ParseObject objectA = new ParseObject("TestObject");
        objectA.put("value", "A");
        objectA.put("foo", "bar");

        ParseObject objectB = new ParseObject("TestObject");
        objectB.put("value", "B");
        objectB.put("foo", "bar");

        ParseQuery.State<ParseObject> queryA = new ParseQuery.State.Builder<>("TestObject")
                .whereEqualTo("value", "A")
                .whereEqualTo("foo", "bar")
                .build();

        assertTrue(matches(logic, queryA, objectA));
        assertFalse(matches(logic, queryA, objectB));
    }

    @Test
    public void testOrMatcher() throws Exception {
        ParseObject objectA = new ParseObject("TestObject");
        objectA.put("value", "A");

        ParseObject objectB = new ParseObject("TestObject");
        objectB.put("value", "B");

        ParseQuery.State<ParseObject> query = ParseQuery.State.Builder.or(Arrays.asList(
                new ParseQuery.State.Builder<>("TestObject")
                        .whereEqualTo("value", "A"),
                new ParseQuery.State.Builder<>("TestObject")
                        .whereEqualTo("value", "B")
        )).build();

        OfflineQueryLogic logic = new OfflineQueryLogic(null);
        assertTrue(matches(logic, query, objectA));
        assertTrue(matches(logic, query, objectB));
    }

    @Test
    public void testAndMatcher() throws Exception {
        ParseObject objectA = new ParseObject("TestObject");
        objectA.put("foo", "bar");

        ParseObject objectB = new ParseObject("TestObject");
        objectB.put("baz", "qux");

        ParseQuery.State<ParseObject> query = new ParseQuery.State.Builder<>("TestObject")
                .addCondition("foo", "$ne", "bar")
                .addCondition("baz", "$ne", "qux")
                .build();

        OfflineQueryLogic logic = new OfflineQueryLogic(null);
        assertFalse(matches(logic, query, objectA));
        assertFalse(matches(logic, query, objectB));
    }

    @Test
    public void testMatchesEqualsWithGeoPoint() throws Exception {
        ParseGeoPoint point = new ParseGeoPoint(37.774929f, -122.419416f); // SF
        ParseObject object = new ParseObject("TestObject");
        object.put("point", point);

        ParseQuery.State<ParseObject> query;
        OfflineQueryLogic logic = new OfflineQueryLogic(null);

        query = new ParseQuery.State.Builder<>("TestObject")
                .whereEqualTo("point", point)
                .build();
        assertTrue(matches(logic, query, object));

        // Test lat
        query = new ParseQuery.State.Builder<>("TestObject")
                .whereEqualTo("point", new ParseGeoPoint(37.774929f, -74.005941f))
                .build();
        assertFalse(matches(logic, query, object));

        // Test lng
        query = new ParseQuery.State.Builder<>("TestObject")
                .whereEqualTo("point", new ParseGeoPoint(40.712784f, -122.419416f))
                .build();
        assertFalse(matches(logic, query, object));

        // Not GeoPoint
        object = new ParseObject("TestObject");
        object.put("point", "A");
        assertFalse(matches(logic, query, object));
    }

    //endregion

    @Test
    public void testMatchesEqualsWithPolygon() throws Exception {
        List<ParseGeoPoint> points = new ArrayList<>();
        points.add(new ParseGeoPoint(0, 0));
        points.add(new ParseGeoPoint(0, 1));
        points.add(new ParseGeoPoint(1, 1));
        points.add(new ParseGeoPoint(1, 0));

        ParsePolygon polygon = new ParsePolygon(points);
        ParseObject object = new ParseObject("TestObject");
        object.put("polygon", polygon);

        ParseQuery.State<ParseObject> query;
        OfflineQueryLogic logic = new OfflineQueryLogic(null);

        query = new ParseQuery.State.Builder<>("TestObject")
                .whereEqualTo("polygon", polygon)
                .build();
        assertTrue(matches(logic, query, object));

        List<ParseGeoPoint> diff = new ArrayList<>();
        diff.add(new ParseGeoPoint(0, 0));
        diff.add(new ParseGeoPoint(0, 10));
        diff.add(new ParseGeoPoint(10, 10));
        diff.add(new ParseGeoPoint(10, 0));
        diff.add(new ParseGeoPoint(0, 0));

        query = new ParseQuery.State.Builder<>("TestObject")
                .whereEqualTo("polygon", new ParsePolygon(diff))
                .build();
        assertFalse(matches(logic, query, object));

        // Not Polygon
        object = new ParseObject("TestObject");
        object.put("polygon", "A");
        assertFalse(matches(logic, query, object));
    }

    @Test
    public void testMatchesEqualsWithNumbers() throws ParseException {
        OfflineQueryLogic logic = new OfflineQueryLogic(null);

        ParseQuery.State<ParseObject> iQuery = new ParseQuery.State.Builder<>("TestObject")
                .whereEqualTo("value", /* (int) */ 5)
                .build();

        ParseObject iObject = new ParseObject("TestObject");
        iObject.put("value", /* (int) */ 5);
        assertTrue(matches(logic, iQuery, iObject));

        ParseObject object = new ParseObject("TestObject");
        object.put("value", "string");
        assertFalse(matches(logic, iQuery, object));

        ParseObject noMatch = new ParseObject("TestObject");
        noMatch.put("value", 6);
        assertFalse(matches(logic, iQuery, noMatch));
    }

    @Test
    public void testMatchesEqualsNull() throws ParseException {
        OfflineQueryLogic logic = new OfflineQueryLogic(null);

        ParseObject object = new ParseObject("TestObject");
        object.put("value", "test");

        ParseObject nullObject = new ParseObject("TestObject");

        ParseQuery.State<ParseObject> query = new ParseQuery.State.Builder<>("TestObject")
                .whereEqualTo("value", "test")
                .build();

        ParseQuery.State<ParseObject> nullQuery = new ParseQuery.State.Builder<>("TestObject")
                .whereEqualTo("value", null)
                .build();

        assertTrue(matches(logic, query, object));
        assertFalse(matches(logic, query, nullObject));

        assertFalse(matches(logic, nullQuery, object));
        assertTrue(matches(logic, nullQuery, nullObject));
    }

    @Test
    public void testMatchesIn() throws ParseException {
        ParseObject object = new ParseObject("TestObject");
        object.put("foo", "bar");

        ParseQuery.State<ParseObject> query;
        OfflineQueryLogic logic = new OfflineQueryLogic(null);

        query = new ParseQuery.State.Builder<>("TestObject")
                .addCondition("foo", "$in", Arrays.asList("bar", "baz"))
                .build();
        assertTrue(matches(logic, query, object));

        query = new ParseQuery.State.Builder<>("TestObject")
                .addCondition("foo", "$in", Collections.singletonList("qux"))
                .build();
        assertFalse(matches(logic, query, object));

        // Non-existant key
        object = new ParseObject("TestObject");
        assertFalse(matches(logic, query, object));
        object.put("foo", JSONObject.NULL);
        assertFalse(matches(logic, query, object));
    }

    @Test
    public void testMatchesAll() throws Exception {
        ParseObject object = new ParseObject("TestObject");
        object.put("foo", Arrays.asList("foo", "bar"));

        ParseQuery.State<ParseObject> query;
        OfflineQueryLogic logic = new OfflineQueryLogic(null);

        query = new ParseQuery.State.Builder<>("TestObject")
                .addCondition("foo", "$all", Arrays.asList("foo", "bar"))
                .build();
        assertTrue(matches(logic, query, object));

        query = new ParseQuery.State.Builder<>("TestObject")
                .addCondition("foo", "$all", Arrays.asList("foo", "bar", "qux"))
                .build();
        assertFalse(matches(logic, query, object));

        // Non-existant key
        object = new ParseObject("TestObject");
        assertFalse(matches(logic, query, object));
        object.put("foo", JSONObject.NULL);
        assertFalse(matches(logic, query, object));

        thrown.expect(IllegalArgumentException.class);
        object.put("foo", "bar");
        assertFalse(matches(logic, query, object));
    }

    @Test
    public void testMatchesAllStartingWith() throws Exception {
        ParseObject object = new ParseObject("TestObject");
        object.put("foo", Arrays.asList("foo", "bar"));

        ParseQuery.State<ParseObject> query;
        OfflineQueryLogic logic = new OfflineQueryLogic(null);

        query = new ParseQuery.State.Builder<>("TestObject")
                .addCondition("foo", "$all",
                        Arrays.asList(
                                buildStartsWithRegexKeyConstraint("foo"),
                                buildStartsWithRegexKeyConstraint("bar")))
                .build();
        assertTrue(matches(logic, query, object));

        query = new ParseQuery.State.Builder<>("TestObject")
                .addCondition("foo", "$all",
                        Arrays.asList(
                                buildStartsWithRegexKeyConstraint("fo"),
                                buildStartsWithRegexKeyConstraint("b")))
                .build();
        assertTrue(matches(logic, query, object));

        query = new ParseQuery.State.Builder<>("TestObject")
                .addCondition("foo", "$all",
                        Arrays.asList(
                                buildStartsWithRegexKeyConstraint("foo"),
                                buildStartsWithRegexKeyConstraint("bar"),
                                buildStartsWithRegexKeyConstraint("qux")))
                .build();
        assertFalse(matches(logic, query, object));

        // Non-existant key
        object = new ParseObject("TestObject");
        assertFalse(matches(logic, query, object));
        object.put("foo", JSONObject.NULL);
        assertFalse(matches(logic, query, object));

        thrown.expect(IllegalArgumentException.class);
        object.put("foo", "bar");
        assertFalse(matches(logic, query, object));
    }

    @Test
    public void testMatchesAllStartingWithParameters() throws Exception {
        ParseObject object = new ParseObject("TestObject");
        object.put("foo", Arrays.asList("foo", "bar"));

        ParseQuery.State<ParseObject> query;
        OfflineQueryLogic logic = new OfflineQueryLogic(null);

        query = new ParseQuery.State.Builder<>("TestObject")
                .addCondition("foo", "$all",
                        Arrays.asList(
                                buildStartsWithRegexKeyConstraint("foo"),
                                buildStartsWithRegexKeyConstraint("bar")))
                .build();
        assertTrue(matches(logic, query, object));

        query = new ParseQuery.State.Builder<>("TestObject")
                .addCondition("foo", "$all",
                        Arrays.asList(
                                buildStartsWithRegexKeyConstraint("fo"),
                                buildStartsWithRegex("ba"),
                                "b"))
                .build();
        thrown.expect(IllegalArgumentException.class);
        assertFalse(matches(logic, query, object));

        query = new ParseQuery.State.Builder<>("TestObject")
                .addCondition("foo", "$all",
                        Arrays.asList(
                                buildStartsWithRegexKeyConstraint("fo"),
                                "b"))
                .build();
        thrown.expect(IllegalArgumentException.class);
        assertFalse(matches(logic, query, object));
    }

    //region matchesWithin

    /**
     * Helper method to convert a string to a key constraint to match strings that starts with given
     * string.
     *
     * @param prefix String to use as prefix in regex.
     * @return The key constraint for word matching at the beginning of a string.
     */
    @NonNull
    private ParseQuery.KeyConstraints buildStartsWithRegexKeyConstraint(String prefix) {
        ParseQuery.KeyConstraints constraint = new ParseQuery.KeyConstraints();
        constraint.put("$regex", buildStartsWithRegex(prefix));
        return constraint;
    }

    /**
     * Helper method to convert a string to regex for start word matching.
     *
     * @param prefix String to use as prefix in regex.
     * @return The string converted as regex for start word matching.
     */
    @NonNull
    private String buildStartsWithRegex(String prefix) {
        return "^" + Pattern.quote(prefix);
    }

    @Test
    public void testMatchesNearSphere() throws Exception {
        ParseGeoPoint fb = new ParseGeoPoint(37.481689f, -122.154949f);
        ParseGeoPoint sf = new ParseGeoPoint(37.774929f, -122.419416f);

        ParseObject object = new ParseObject("TestObject");
        object.put("point", fb);

        ParseQuery.State<ParseObject> query;
        OfflineQueryLogic logic = new OfflineQueryLogic(null);

        query = new ParseQuery.State.Builder<>("TestObject")
                .whereNear("point", fb)
                .build();
        assertTrue(matches(logic, query, object));

        query = new ParseQuery.State.Builder<>("TestObject")
                .whereNear("point", sf)
                .maxDistance("point", 0.00628)
                .build();
        assertFalse(matches(logic, query, object));

        query = new ParseQuery.State.Builder<>("TestObject")
                .whereNear("point", sf)
                .maxDistance("point", 0.00629)
                .build();
        assertTrue(matches(logic, query, object));

        // Non-existant key
        object = new ParseObject("TestObject");
        assertFalse(matches(logic, query, object));
    }

    @Test
    public void testMatchesWithinFailureInternationalDateLine() throws ParseException {
        ParseGeoPoint fb = new ParseGeoPoint(37.481689f, -122.154949f);
        ParseGeoPoint sf = new ParseGeoPoint(37.774929f, -122.419416f);
        ParseGeoPoint sj = new ParseGeoPoint(37.338208f, -121.886329f);

        ParseObject object = new ParseObject("TestObject");
        object.put("point", fb);

        ParseQuery.State<ParseObject> query;
        OfflineQueryLogic logic = new OfflineQueryLogic(null);

        thrown.expect(ParseException.class);
        thrown.expect(hasParseErrorCode(ParseException.INVALID_QUERY));
        thrown.expectMessage("whereWithinGeoBox queries cannot cross the International Date Line.");
        query = new ParseQuery.State.Builder<>("TestObject")
                .whereWithin("point", sj, sf)
                .build();
        matches(logic, query, object);
    }

    @Test
    public void testMatchesWithinFailureSwapped() throws Exception {
        ParseGeoPoint fb = new ParseGeoPoint(37.481689f, -122.154949f);
        ParseGeoPoint sf = new ParseGeoPoint(37.774929f, -122.419416f);
        ParseGeoPoint sj = new ParseGeoPoint(37.338208f, -121.886329f);

        ParseObject object = new ParseObject("TestObject");
        object.put("point", fb);

        ParseQuery.State<ParseObject> query;
        OfflineQueryLogic logic = new OfflineQueryLogic(null);

        thrown.expect(ParseException.class);
        thrown.expect(hasParseErrorCode(ParseException.INVALID_QUERY));
        thrown.expectMessage(
                "The southwest corner of a geo box must be south of the northeast corner.");
        query = new ParseQuery.State.Builder<>("TestObject")
                .whereWithin("point", sf, sj)
                .build();
        matches(logic, query, object);
    }

    @Test
    public void testMatchesWithinFailure180() throws Exception {
        ParseGeoPoint fb = new ParseGeoPoint(37.481689f, -122.154949f);
        ParseGeoPoint sf = new ParseGeoPoint(37.774929f, -122.419416f);
        ParseGeoPoint beijing = new ParseGeoPoint(39.904211f, 116.407395f);

        ParseObject object = new ParseObject("TestObject");
        object.put("point", fb);

        ParseQuery.State<ParseObject> query;
        OfflineQueryLogic logic = new OfflineQueryLogic(null);

        thrown.expect(ParseException.class);
        thrown.expect(hasParseErrorCode(ParseException.INVALID_QUERY));
        thrown.expectMessage("Geo box queries larger than 180 degrees in longitude are not supported. "
                + "Please check point order.");
        query = new ParseQuery.State.Builder<>("TestObject")
                .whereWithin("point", sf, beijing)
                .build();
        matches(logic, query, object);
    }

    //endregion

    //region compare

    @Test
    public void testMatchesWithin() throws ParseException {
        ParseGeoPoint fb = new ParseGeoPoint(37.481689f, -122.154949f);
        ParseGeoPoint sunset = new ParseGeoPoint(37.746731f, -122.486349f);
        ParseGeoPoint soma = new ParseGeoPoint(37.778519f, -122.40564f);
        ParseGeoPoint twinPeaks = new ParseGeoPoint(37.754407f, -122.447684f);

        ParseObject object = new ParseObject("TestObject");
        object.put("point", fb);

        ParseQuery.State<ParseObject> query;
        OfflineQueryLogic logic = new OfflineQueryLogic(null);

        object.put("point", twinPeaks);
        query = new ParseQuery.State.Builder<>("TestObject")
                .whereWithin("point", sunset, soma)
                .build();
        assertTrue(matches(logic, query, object));

        object.put("point", fb);
        assertFalse(matches(logic, query, object));

        // Non-existant key
        object = new ParseObject("TestObject");
        assertFalse(matches(logic, query, object));
    }

    @Test
    public void testMatchesGeoIntersects() throws ParseException {
        List<ParseGeoPoint> points = new ArrayList<>();
        points.add(new ParseGeoPoint(0, 0));
        points.add(new ParseGeoPoint(0, 1));
        points.add(new ParseGeoPoint(1, 1));
        points.add(new ParseGeoPoint(1, 0));

        ParseGeoPoint inside = new ParseGeoPoint(0.5, 0.5);
        ParseGeoPoint outside = new ParseGeoPoint(10, 10);

        ParsePolygon polygon = new ParsePolygon(points);

        ParseObject object = new ParseObject("TestObject");
        object.put("polygon", polygon);

        ParseQuery.State<ParseObject> query;
        OfflineQueryLogic logic = new OfflineQueryLogic(null);
        query = new ParseQuery.State.Builder<>("TestObject")
                .whereGeoIntersects("polygon", inside)
                .build();
        assertTrue(matches(logic, query, object));

        query = new ParseQuery.State.Builder<>("TestObject")
                .whereGeoIntersects("polygon", outside)
                .build();
        assertFalse(matches(logic, query, object));

        // Non-existant key
        object = new ParseObject("TestObject");
        assertFalse(matches(logic, query, object));
    }

    //endregion

    //region compareTo

    @Test
    public void testMatchesGeoWithin() throws ParseException {
        List<ParseGeoPoint> smallBox = new ArrayList<>();
        smallBox.add(new ParseGeoPoint(0, 0));
        smallBox.add(new ParseGeoPoint(0, 1));
        smallBox.add(new ParseGeoPoint(1, 1));
        smallBox.add(new ParseGeoPoint(1, 0));

        List<ParseGeoPoint> largeBox = new ArrayList<>();
        largeBox.add(new ParseGeoPoint(0, 0));
        largeBox.add(new ParseGeoPoint(0, 10));
        largeBox.add(new ParseGeoPoint(10, 10));
        largeBox.add(new ParseGeoPoint(10, 0));

        ParseGeoPoint point = new ParseGeoPoint(5, 5);

        //ParsePolygon polygon = new ParsePolygon(points);

        ParseObject object = new ParseObject("TestObject");
        object.put("point", point);

        ParseQuery.State<ParseObject> query;
        OfflineQueryLogic logic = new OfflineQueryLogic(null);
        query = new ParseQuery.State.Builder<>("TestObject")
                .whereGeoWithin("point", largeBox)
                .build();
        assertTrue(matches(logic, query, object));

        query = new ParseQuery.State.Builder<>("TestObject")
                .whereGeoWithin("point", smallBox)
                .build();
        assertFalse(matches(logic, query, object));

        // Non-existant key
        object = new ParseObject("TestObject");
        assertFalse(matches(logic, query, object));
    }

    @Test
    public void testCompareList() throws Exception {
        ParseObject object = new ParseObject("SomeObject");
        List<Integer> list = new ArrayList<>();
        list.add(1);
        list.add(2);
        list.add(3);
        object.put("list", list);

        ParseQuery.State<ParseObject> query;
        OfflineQueryLogic logic = new OfflineQueryLogic(null);

        query = new ParseQuery.State.Builder<>("SomeObject")
                .whereEqualTo("list", 2)
                .build();
        assertTrue(matches(logic, query, object));

        query = new ParseQuery.State.Builder<>("SomeObject")
                .whereEqualTo("list", 4)
                .build();
        assertFalse(matches(logic, query, object));
    }

    //endregion

    //region Sort

    @Test
    public void testCompareJSONArray() throws Exception {
        ParseObject object = new ParseObject("SomeObject");
        JSONArray array = new JSONArray();
        array.put(1);
        array.put(2);
        array.put(3);
        object.put("array", array);

        ParseQuery.State<ParseObject> query;
        OfflineQueryLogic logic = new OfflineQueryLogic(null);

        query = new ParseQuery.State.Builder<>("SomeObject")
                .whereEqualTo("array", 2)
                .build();
        assertTrue(matches(logic, query, object));

        query = new ParseQuery.State.Builder<>("SomeObject")
                .whereEqualTo("array", 4)
                .build();
        assertFalse(matches(logic, query, object));
    }

    @Test
    public void testCompareToNumber() throws Exception {
        ParseObject object = new ParseObject("TestObject");
        object.put("value", 5);

        ParseQuery.State<ParseObject> query;
        OfflineQueryLogic logic = new OfflineQueryLogic(null);

        query = new ParseQuery.State.Builder<>("TestObject")
                .whereEqualTo("value", /* (int) */ 5)
                .build();
        assertTrue(matches(logic, query, object));
        object.put("value", 6);
        assertFalse(matches(logic, query, object));
        object.put("value", 5);

        query = new ParseQuery.State.Builder<>("TestObject")
                .addCondition("value", "$lt", 6)
                .build();
        assertTrue(matches(logic, query, object));
        object.put("value", 6);
        assertFalse(matches(logic, query, object));
        object.put("value", 5);

        query = new ParseQuery.State.Builder<>("TestObject")
                .addCondition("value", "$gt", 4)
                .build();
        assertTrue(matches(logic, query, object));
        object.put("value", 4);
        assertFalse(matches(logic, query, object));
        object.put("value", 5);

        // TODO(grantland): Move below to NumbersTest

        ParseObject iObject = new ParseObject("TestObject");
        iObject.put("value", /* (int) */ 5);

        ParseObject dObject = new ParseObject("TestObject");
        dObject.put("value", /* (double) */ 5.0);

        ParseObject fObject = new ParseObject("TestObject");
        fObject.put("value", /* (float) */ 5.0f);

        ParseObject lObject = new ParseObject("TestObject");
        lObject.put("value", (long) 5);

        ParseQuery.State<ParseObject> iQuery = new ParseQuery.State.Builder<>("TestObject")
                .whereEqualTo("value", /* (int) */ 5)
                .build();

        ParseQuery.State<ParseObject> dQuery = new ParseQuery.State.Builder<>("TestObject")
                .whereEqualTo("value", /* (double) */ 5.0)
                .build();

        ParseQuery.State<ParseObject> fQuery = new ParseQuery.State.Builder<>("TestObject")
                .whereEqualTo("value", /* (float) */ 5.0f)
                .build();

        ParseQuery.State<ParseObject> lQuery = new ParseQuery.State.Builder<>("TestObject")
                .whereEqualTo("value", (long) 5)
                .build();

        assertTrue(matches(logic, iQuery, iObject));
        assertTrue(matches(logic, iQuery, dObject));
        assertTrue(matches(logic, iQuery, fObject));
        assertTrue(matches(logic, iQuery, lObject));

        assertTrue(matches(logic, dQuery, iObject));
        assertTrue(matches(logic, dQuery, dObject));
        assertTrue(matches(logic, dQuery, fObject));
        assertTrue(matches(logic, dQuery, lObject));

        assertTrue(matches(logic, fQuery, iObject));
        assertTrue(matches(logic, fQuery, dObject));
        assertTrue(matches(logic, fQuery, fObject));
        assertTrue(matches(logic, fQuery, lObject));

        assertTrue(matches(logic, lQuery, iObject));
        assertTrue(matches(logic, lQuery, dObject));
        assertTrue(matches(logic, lQuery, fObject));
        assertTrue(matches(logic, lQuery, lObject));
    }

    @Test
    public void testCompareToDate() throws Exception {
        Date date = new Date();
        Date before = new Date(date.getTime() - 20);
        Date after = new Date(date.getTime() + 20);

        ParseObject object = new ParseObject("TestObject");
        object.put("date", date);

        ParseQuery.State<ParseObject> query;
        OfflineQueryLogic logic = new OfflineQueryLogic(null);

        query = new ParseQuery.State.Builder<>("TestObject")
                .whereEqualTo("date", date)
                .build();
        assertTrue(matches(logic, query, object));
        object.put("date", after);
        assertFalse(matches(logic, query, object));
        object.put("date", date);

        query = new ParseQuery.State.Builder<>("TestObject")
                .addCondition("date", "$lt", after)
                .build();
        assertTrue(matches(logic, query, object));
        object.put("date", after);
        assertFalse(matches(logic, query, object));
        object.put("date", date);

        query = new ParseQuery.State.Builder<>("TestObject")
                .addCondition("date", "$gt", before)
                .build();
        assertTrue(matches(logic, query, object));
        object.put("date", before);
        assertFalse(matches(logic, query, object));
        object.put("date", after);
    }

    @Test
    public void testSortInvalidKey() throws ParseException {
        ParseQuery.State<ParseObject> query = new ParseQuery.State.Builder<>("TestObject")
                .addAscendingOrder("_test")
                .build();

        thrown.expect(ParseException.class);
        OfflineQueryLogic.sort(null, query);
    }

    @Test
    public void testSortWithNoOrder() throws ParseException {
        ParseQuery.State<ParseObject> query = new ParseQuery.State.Builder<>("TestObject")
                .build();

        OfflineQueryLogic.sort(null, query);
    }

    @Test
    public void testSortWithGeoQuery() throws ParseException {
        ParseGeoPoint fb = new ParseGeoPoint(37.481689f, -122.154949f);

        ParseQuery.State<ParseObject> query = new ParseQuery.State.Builder<>("TestObject")
                .whereNear("point", fb)
                .build();

        List<ParseObject> objects = new ArrayList<>();
        ParseObject object;

        object = new ParseObject("TestObject");
        object.put("name", "sf");
        object.put("point", new ParseGeoPoint(37.774929f, -122.419416f));
        objects.add(object);

        object = new ParseObject("TestObject");
        object.put("name", "ny");
        object.put("point", new ParseGeoPoint(40.712784f, -74.005941f));
        objects.add(object);

        object = new ParseObject("TestObject");
        object.put("name", "mpk");
        object.put("point", new ParseGeoPoint(37.452960f, -122.181725f));
        objects.add(object);

        OfflineQueryLogic.sort(objects, query);
        assertEquals("mpk", objects.get(0).getString("name"));
        assertEquals("sf", objects.get(1).getString("name"));
        assertEquals("ny", objects.get(2).getString("name"));
    }

    @Test
    public void testSortDescending() throws ParseException {
        ParseQuery.State<ParseObject> query = new ParseQuery.State.Builder<>("TestObject")
                .addDescendingOrder("name")
                .build();

        List<ParseObject> objects = new ArrayList<>();
        ParseObject object;

        object = new ParseObject("TestObject");
        object.put("name", "grantland");
        objects.add(object);
        object = new ParseObject("TestObject");
        object.put("name", "nikita");
        objects.add(object);
        object = new ParseObject("TestObject");
        object.put("name", "listiarso");
        objects.add(object);

        OfflineQueryLogic.sort(objects, query);
        assertEquals("nikita", objects.get(0).getString("name"));
        assertEquals("listiarso", objects.get(1).getString("name"));
        assertEquals("grantland", objects.get(2).getString("name"));
    }

    //endregion

    //region fetchIncludes

    @Test
    public void testQuerySortNumber() throws ParseException {
        ParseQuery.State<ParseObject> query = new ParseQuery.State.Builder<>("TestObject")
                .addAscendingOrder("key")
                .build();

        List<ParseObject> results = generateParseObjects("key", new Object[]{
                /* (int) */ 8,
                /* (double) */ 7.0,
                /* (float) */ 6.0f,
                (long) 5
        });

        OfflineQueryLogic.sort(results, query);

        int last = 0;
        for (ParseObject result : results) {
            int current = result.getInt("key");
            assertTrue(current > last);
            last = current;
        }
    }

    @Test
    public void testQuerySortNull() throws ParseException {
        ParseQuery.State<ParseObject> query = new ParseQuery.State.Builder<>("TestObject")
                .addAscendingOrder("key")
                .build();

        List<ParseObject> results = generateParseObjects("key", new Object[]{
                null,
                "value",
                null
        });

        OfflineQueryLogic.sort(results, query);

        assertEquals(0, results.get(0).getInt("id"));
        assertEquals(2, results.get(1).getInt("id"));
        assertEquals(1, results.get(2).getInt("id"));
    }

    @Test
    public void testQuerySortDifferentTypes() throws ParseException {
        ParseQuery.State<ParseObject> query = new ParseQuery.State.Builder<>("TestObject")
                .addAscendingOrder("key")
                .build();

        List<ParseObject> results = generateParseObjects("key", new Object[]{
                "string",
                5
        });

        thrown.expect(IllegalArgumentException.class);
        OfflineQueryLogic.sort(results, query);
    }

    @Test
    public void testFetchIncludesParseObject() throws ParseException {
        OfflineStore store = mock(OfflineStore.class);
        when(store.fetchLocallyAsync(any(ParseObject.class), any(ParseSQLiteDatabase.class)))
                .thenReturn(Task.<ParseObject>forResult(null));

        ParseSQLiteDatabase db = mock(ParseSQLiteDatabase.class);

        ParseQuery.State<ParseObject> query = new ParseQuery.State.Builder<>("TestObject")
                .include("foo")
                .build();

        ParseObject object = new ParseObject("TestObject");
        ParseObject unfetchedObject = new ParseObject("TestObject");
        object.put("foo", unfetchedObject);

        ParseTaskUtils.wait(OfflineQueryLogic.fetchIncludesAsync(store, object, query, db));
        verify(store).fetchLocallyAsync(object, db);
        verify(store).fetchLocallyAsync(unfetchedObject, db);
        verifyNoMoreInteractions(store);
    }

    @Test
    public void testFetchIncludesCollection() throws ParseException {
        OfflineStore store = mock(OfflineStore.class);
        when(store.fetchLocallyAsync(any(ParseObject.class), any(ParseSQLiteDatabase.class)))
                .thenReturn(Task.<ParseObject>forResult(null));

        ParseSQLiteDatabase db = mock(ParseSQLiteDatabase.class);

        ParseQuery.State<ParseObject> query = new ParseQuery.State.Builder<>("TestObject")
                .include("foo")
                .build();

        ParseObject object = mock(ParseObject.class);
        ParseObject unfetchedObject = mock(ParseObject.class);
        Collection<ParseObject> objects = new ArrayList<>();
        objects.add(unfetchedObject);
        when(object.get("foo")).thenReturn(objects);

        ParseTaskUtils.wait(OfflineQueryLogic.fetchIncludesAsync(store, object, query, db));
        verify(store).fetchLocallyAsync(object, db);
        verify(store).fetchLocallyAsync(unfetchedObject, db);
        verifyNoMoreInteractions(store);
    }

    @Test
    public void testFetchIncludesJSONArray() throws ParseException {
        OfflineStore store = mock(OfflineStore.class);
        when(store.fetchLocallyAsync(any(ParseObject.class), any(ParseSQLiteDatabase.class)))
                .thenReturn(Task.<ParseObject>forResult(null));

        ParseSQLiteDatabase db = mock(ParseSQLiteDatabase.class);

        ParseQuery.State<ParseObject> query = new ParseQuery.State.Builder<>("TestObject")
                .include("foo")
                .build();

        ParseObject object = mock(ParseObject.class);
        ParseObject unfetchedObject = mock(ParseObject.class);
        JSONArray objects = new JSONArray();
        objects.put(unfetchedObject);
        when(object.get("foo")).thenReturn(objects);

        ParseTaskUtils.wait(OfflineQueryLogic.fetchIncludesAsync(store, object, query, db));
        verify(store).fetchLocallyAsync(object, db);
        verify(store).fetchLocallyAsync(unfetchedObject, db);
        verifyNoMoreInteractions(store);
    }

    @Test
    public void testFetchIncludesMap() throws ParseException {
        OfflineStore store = mock(OfflineStore.class);
        when(store.fetchLocallyAsync(any(ParseObject.class), any(ParseSQLiteDatabase.class)))
                .thenReturn(Task.<ParseObject>forResult(null));

        ParseSQLiteDatabase db = mock(ParseSQLiteDatabase.class);

        ParseQuery.State<ParseObject> query = new ParseQuery.State.Builder<>("TestObject")
                .include("foo.bar")
                .build();

        ParseObject object = mock(ParseObject.class);
        ParseObject unfetchedObject = mock(ParseObject.class);
        Map<String, ParseObject> objects = new HashMap<>();
        objects.put("bar", unfetchedObject);
        when(object.get("foo")).thenReturn(objects);

        ParseTaskUtils.wait(OfflineQueryLogic.fetchIncludesAsync(store, object, query, db));
        verify(store).fetchLocallyAsync(object, db);
        verify(store).fetchLocallyAsync(unfetchedObject, db);
        verifyNoMoreInteractions(store);
    }

    @Test
    public void testFetchIncludesJSONObject() throws Exception {
        OfflineStore store = mock(OfflineStore.class);
        when(store.fetchLocallyAsync(any(ParseObject.class), any(ParseSQLiteDatabase.class)))
                .thenReturn(Task.<ParseObject>forResult(null));

        ParseSQLiteDatabase db = mock(ParseSQLiteDatabase.class);

        ParseQuery.State<ParseObject> query = new ParseQuery.State.Builder<>("TestObject")
                .include("foo.bar")
                .build();

        ParseObject object = mock(ParseObject.class);
        ParseObject unfetchedObject = mock(ParseObject.class);
        JSONObject objects = new JSONObject();
        objects.put("bar", unfetchedObject);
        when(object.get("foo")).thenReturn(objects);

        ParseTaskUtils.wait(OfflineQueryLogic.fetchIncludesAsync(store, object, query, db));
        verify(store).fetchLocallyAsync(object, db);
        verify(store).fetchLocallyAsync(unfetchedObject, db);
        verifyNoMoreInteractions(store);
    }

    @Test
    public void testFetchIncludesNull() throws ParseException {
        OfflineStore store = mock(OfflineStore.class);
        when(store.fetchLocallyAsync(any(ParseObject.class), any(ParseSQLiteDatabase.class)))
                .thenReturn(Task.<ParseObject>forResult(null));

        ParseQuery.State<ParseObject> query = new ParseQuery.State.Builder<>("TestObject")
                .include("foo")
                .build();

        ParseObject object = new ParseObject("TestObject");
        object.put("foo", JSONObject.NULL);

        ParseTaskUtils.wait(OfflineQueryLogic.fetchIncludesAsync(store, object, query, null));
        // only itself
        verify(store, times(1))
                .fetchLocallyAsync(any(ParseObject.class), any(ParseSQLiteDatabase.class));
    }

    @Test
    public void testFetchIncludesNonParseObject() throws ParseException {
        OfflineStore store = mock(OfflineStore.class);
        when(store.fetchLocallyAsync(any(ParseObject.class), any(ParseSQLiteDatabase.class)))
                .thenReturn(Task.<ParseObject>forResult(null));

        ParseQuery.State<ParseObject> query = new ParseQuery.State.Builder<>("TestObject")
                .include("foo")
                .build();

        ParseObject object = new ParseObject("TestObject");
        object.put("foo", 1);

        thrown.expect(ParseException.class);
        ParseTaskUtils.wait(OfflineQueryLogic.fetchIncludesAsync(store, object, query, null));
        // only itself
        verify(store, times(1))
                .fetchLocallyAsync(any(ParseObject.class), any(ParseSQLiteDatabase.class));
    }

    //endregion

    @Test
    public void testFetchIncludesDoesNotExist() throws ParseException {
        OfflineStore store = mock(OfflineStore.class);
        when(store.fetchLocallyAsync(any(ParseObject.class), any(ParseSQLiteDatabase.class)))
                .thenReturn(Task.<ParseObject>forResult(null));

        ParseQuery.State<ParseObject> query = new ParseQuery.State.Builder<>("TestObject")
                .include("foo")
                .build();

        ParseObject object = new ParseObject("TestObject");

        ParseTaskUtils.wait(OfflineQueryLogic.fetchIncludesAsync(store, object, query, null));
        // only itself
        verify(store, times(1))
                .fetchLocallyAsync(any(ParseObject.class), any(ParseSQLiteDatabase.class));
    }

    @Test
    public void testFetchIncludesNestedNull() throws Exception {
        OfflineStore store = mock(OfflineStore.class);
        when(store.fetchLocallyAsync(any(ParseObject.class), any(ParseSQLiteDatabase.class)))
                .thenReturn(Task.<ParseObject>forResult(null));

        ParseQuery.State<ParseObject> query = new ParseQuery.State.Builder<>("TestObject")
                .include("foo.bar")
                .build();

        ParseObject object = new ParseObject("TestObject");
        object.put("foo", JSONObject.NULL);

        ParseTaskUtils.wait(OfflineQueryLogic.fetchIncludesAsync(store, object, query, null));
        // only itself
        verify(store, times(1))
                .fetchLocallyAsync(any(ParseObject.class), any(ParseSQLiteDatabase.class));
    }

    @Test
    public void testFetchIncludesNestedNonParseObject() throws Exception {
        OfflineStore store = mock(OfflineStore.class);
        when(store.fetchLocallyAsync(any(ParseObject.class), any(ParseSQLiteDatabase.class)))
                .thenReturn(Task.<ParseObject>forResult(null));

        ParseQuery.State<ParseObject> query = new ParseQuery.State.Builder<>("TestObject")
                .include("foo.bar")
                .build();

        ParseObject object = new ParseObject("TestObject");
        object.put("foo", 1);

        thrown.expect(IllegalStateException.class);
        ParseTaskUtils.wait(OfflineQueryLogic.fetchIncludesAsync(store, object, query, null));
        // only itself
        verify(store, times(1))
                .fetchLocallyAsync(any(ParseObject.class), any(ParseSQLiteDatabase.class));
    }
}

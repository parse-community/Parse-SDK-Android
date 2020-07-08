/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import android.os.Parcel;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.parse.boltsinternal.Task;
import com.parse.boltsinternal.TaskCompletionSource;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
@Config(constants = BuildConfig.class, sdk = TestHelper.ROBOLECTRIC_SDK_VERSION)
public class ParseObjectTest {

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private static void mockCurrentUserController() {
        ParseCurrentUserController userController = mock(ParseCurrentUserController.class);
        when(userController.getCurrentSessionTokenAsync()).thenReturn(Task.forResult("token"));
        when(userController.getAsync()).thenReturn(Task.<ParseUser>forResult(null));
        ParseCorePlugins.getInstance().registerCurrentUserController(userController);
    }

    // Returns a tcs to control the operation.
    private static TaskCompletionSource<ParseObject.State> mockObjectControllerForSave() {
        TaskCompletionSource<ParseObject.State> tcs = new TaskCompletionSource<>();
        ParseObjectController objectController = mock(ParseObjectController.class);
        when(objectController.saveAsync(
                any(ParseObject.State.class), any(ParseOperationSet.class),
                anyString(), any(ParseDecoder.class))
        ).thenReturn(tcs.getTask());
        ParseCorePlugins.getInstance().registerObjectController(objectController);
        return tcs;
    }

    // Returns a tcs to control the operation.
    private static TaskCompletionSource<Void> mockObjectControllerForDelete() {
        TaskCompletionSource<Void> tcs = new TaskCompletionSource<>();
        ParseObjectController objectController = mock(ParseObjectController.class);
        when(objectController.deleteAsync(
                any(ParseObject.State.class), anyString())
        ).thenReturn(tcs.getTask());
        ParseCorePlugins.getInstance().registerObjectController(objectController);
        return tcs;
    }

    @Before
    public void setUp() {
        ParseFieldOperations.registerDefaultDecoders(); // to test JSON / Parcel decoding
    }

    //region testRevert

    @After
    public void tearDown() {
        ParseCorePlugins.getInstance().reset();
        ParsePlugins.reset();
    }

    @Test
    public void testFromJSONPayload() throws JSONException {
        JSONObject json = new JSONObject(
                "{" +
                        "\"className\":\"GameScore\"," +
                        "\"createdAt\":\"2015-06-22T21:23:41.733Z\"," +
                        "\"objectId\":\"TT1ZskATqS\"," +
                        "\"updatedAt\":\"2015-06-22T22:06:18.104Z\"," +
                        "\"score\":{" +
                        "\"__op\":\"Increment\"," +
                        "\"amount\":1" +
                        "}," +
                        "\"age\":33" +
                        "}");

        ParseObject parseObject = ParseObject.fromJSONPayload(json, ParseDecoder.get());
        assertEquals("GameScore", parseObject.getClassName());
        assertEquals("TT1ZskATqS", parseObject.getObjectId());
        ParseDateFormat format = ParseDateFormat.getInstance();
        assertTrue(parseObject.getCreatedAt().equals(format.parse("2015-06-22T21:23:41.733Z")));
        assertTrue(parseObject.getUpdatedAt().equals(format.parse("2015-06-22T22:06:18.104Z")));

        Set<String> keys = parseObject.getState().keySet();
        assertEquals(0, keys.size());

        ParseOperationSet currentOperations = parseObject.operationSetQueue.getLast();
        assertEquals(2, currentOperations.size());
    }

    //endregion

    //region testFromJson

    @Test
    public void testFromJSONPayloadWithoutClassname() throws JSONException {
        JSONObject json = new JSONObject("{\"objectId\":\"TT1ZskATqS\"}");
        ParseObject parseObject = ParseObject.fromJSONPayload(json, ParseDecoder.get());
        assertNull(parseObject);
    }

    @Test
    public void testFromJsonWithLdsStackOverflow() throws JSONException {
        ParseObject localObj = ParseObject.createWithoutData("GameScore", "TT1ZskATqS");
        OfflineStore lds = mock(OfflineStore.class);
        Parse.setLocalDatastore(lds);

        when(lds.getObject(eq("GameScore"), eq("TT1ZskATqS"))).thenReturn(localObj);

        JSONObject json = new JSONObject("{" +
                "\"className\":\"GameScore\"," +
                "\"createdAt\":\"2015-06-22T21:23:41.733Z\"," +
                "\"objectId\":\"TT1ZskATqS\"," +
                "\"updatedAt\":\"2015-06-22T22:06:18.104Z\"" +
                "}");
        ParseObject obj;
        for (int i = 0; i < 50000; i++) {
            obj = ParseObject.fromJSON(json, "GameScore", ParseDecoder.get(), Collections.<String>emptySet());
        }
    }

    //endregion

    //region testGetter

    @Test
    public void testRevert() throws ParseException {
        List<Task<Void>> tasks = new ArrayList<>();

        // Mocked to let save work
        mockCurrentUserController();

        // Mocked to simulate in-flight save
        TaskCompletionSource<ParseObject.State> tcs = mockObjectControllerForSave();

        // New clean object
        ParseObject object = new ParseObject("TestObject");
        object.revert("foo");

        // Reverts changes on new object
        object.put("foo", "bar");
        object.put("name", "grantland");
        object.revert();
        assertNull(object.get("foo"));
        assertNull(object.get("name"));

        // Object from server
        ParseObject.State state = mock(ParseObject.State.class);
        when(state.className()).thenReturn("TestObject");
        when(state.objectId()).thenReturn("test_id");
        when(state.keySet()).thenReturn(Collections.singleton("foo"));
        when(state.get("foo")).thenReturn("bar");
        object = ParseObject.from(state);
        object.revert();
        assertFalse(object.isDirty());
        assertEquals("bar", object.get("foo"));

        // Reverts changes on existing object
        object.put("foo", "baz");
        object.put("name", "grantland");
        object.revert();
        assertFalse(object.isDirty());
        assertEquals("bar", object.get("foo"));
        assertFalse(object.isDataAvailable("name"));

        // Shouldn't revert changes done before last call to `save`
        object.put("foo", "baz");
        object.put("name", "nlutsenko");
        tasks.add(object.saveInBackground());
        object.revert();
        assertFalse(object.isDirty());
        assertEquals("baz", object.get("foo"));
        assertEquals("nlutsenko", object.get("name"));

        // Should revert changes done after last call to `save`
        object.put("foo", "qux");
        object.put("name", "grantland");
        object.revert();
        assertFalse(object.isDirty());
        assertEquals("baz", object.get("foo"));
        assertEquals("nlutsenko", object.get("name"));

        // Allow save to complete
        tcs.setResult(state);
        ParseTaskUtils.wait(Task.whenAll(tasks));
    }

    @Test
    public void testRevertKey() throws ParseException {
        List<Task<Void>> tasks = new ArrayList<>();

        // Mocked to let save work
        mockCurrentUserController();

        // Mocked to simulate in-flight save
        TaskCompletionSource<ParseObject.State> tcs = mockObjectControllerForSave();

        // New clean object
        ParseObject object = new ParseObject("TestObject");
        object.revert("foo");

        // Reverts changes on new object
        object.put("foo", "bar");
        object.put("name", "grantland");
        object.revert("foo");
        assertNull(object.get("foo"));
        assertEquals("grantland", object.get("name"));

        // Object from server
        ParseObject.State state = mock(ParseObject.State.class);
        when(state.className()).thenReturn("TestObject");
        when(state.objectId()).thenReturn("test_id");
        when(state.keySet()).thenReturn(Collections.singleton("foo"));
        when(state.get("foo")).thenReturn("bar");
        object = ParseObject.from(state);
        object.revert("foo");
        assertFalse(object.isDirty());
        assertEquals("bar", object.get("foo"));

        // Reverts changes on existing object
        object.put("foo", "baz");
        object.put("name", "grantland");
        object.revert("foo");
        assertEquals("bar", object.get("foo"));
        assertEquals("grantland", object.get("name"));

        // Shouldn't revert changes done before last call to `save`
        object.put("foo", "baz");
        object.put("name", "nlutsenko");
        tasks.add(object.saveInBackground());
        object.revert("foo");
        assertEquals("baz", object.get("foo"));
        assertEquals("nlutsenko", object.get("name"));

        // Should revert changes done after last call to `save`
        object.put("foo", "qux");
        object.put("name", "grantland");
        object.revert("foo");
        assertEquals("baz", object.get("foo"));
        assertEquals("grantland", object.get("name"));

        // Allow save to complete
        tcs.setResult(state);
        ParseTaskUtils.wait(Task.whenAll(tasks));
    }

    @Test(expected = IllegalStateException.class)
    public void testGetUnavailable() {
        ParseObject.State state = mock(ParseObject.State.class);
        when(state.className()).thenReturn("TestObject");
        when(state.isComplete()).thenReturn(false);
        ParseObject object = ParseObject.from(state);
        object.get("foo");
    }

    @Test
    public void testGetAvailableIfKeyAvailable() {
        ParseObject.State state = mock(ParseObject.State.class);
        when(state.className()).thenReturn("TestObject");
        when(state.isComplete()).thenReturn(false);
        when(state.availableKeys()).thenReturn(new HashSet<>(Collections.singletonList("foo")));
        ParseObject object = ParseObject.from(state);
        object.get("foo");
    }

    @Test
    public void testGetList() {
        ParseObject object = new ParseObject("Test");
        JSONArray array = new JSONArray();
        array.put("value");
        array.put("valueAgain");
        object.put("key", array);

        List list = object.getList("key");

        assertEquals(2, list.size());
        assertTrue(list.contains("value"));
        assertTrue(list.contains("valueAgain"));
    }

    @Test
    public void testGetListWithWrongValue() {
        ParseObject object = new ParseObject("Test");
        object.put("key", 1);

        assertNull(object.getList("key"));
    }

    @Test
    public void testGetJSONArray() throws Exception {
        ParseObject object = new ParseObject("Test");
        object.put("key", Arrays.asList("value", "valueAgain"));

        JSONArray array = object.getJSONArray("key");

        assertEquals(2, array.length());
        assertEquals("value", array.getString(0));
        assertEquals("valueAgain", array.getString(1));
    }

    @Test
    public void testGetJsonArrayWithWrongValue() {
        ParseObject object = new ParseObject("Test");
        object.put("key", 1);

        assertNull(object.getJSONArray("key"));
    }

    @Test
    public void testGetJSONObject() throws Exception {
        ParseObject object = new ParseObject("Test");
        Map<String, String> map = new HashMap<>();
        map.put("key", "value");
        map.put("keyAgain", "valueAgain");
        object.put("key", map);

        JSONObject json = object.getJSONObject("key");

        assertEquals(2, json.length());
        assertEquals("value", json.getString("key"));
        assertEquals("valueAgain", json.getString("keyAgain"));
    }

    @Test
    public void testGetJsonObjectWithWrongValue() {
        ParseObject object = new ParseObject("Test");
        object.put("key", 1);

        assertNull(object.getJSONObject("key"));
    }

    @Test
    public void testGetBoolean() {
        ParseObject object = new ParseObject("Test");
        object.put("key", true);

        assertTrue(object.getBoolean("key"));
    }

    @Test
    public void testGetBooleanWithWrongValue() {
        ParseObject object = new ParseObject("Test");
        object.put("key", 1);

        assertFalse(object.getBoolean("key"));
    }

    @Test
    public void testGetDate() {
        ParseObject object = new ParseObject("Test");
        Date date = new Date();
        object.put("key", date);

        assertEquals(date, object.getDate("key"));
    }

    @Test
    public void testGetDateWithWrongValue() {
        ParseObject object = new ParseObject("Test");
        object.put("key", 1);

        assertNull(object.getDate("key"));
    }

    @Test
    public void testGetParseGeoPoint() {
        ParseObject object = new ParseObject("Test");
        ParseGeoPoint point = new ParseGeoPoint(10, 10);
        object.put("key", point);

        assertEquals(point, object.getParseGeoPoint("key"));
    }

    @Test
    public void testGetParseGeoPointWithWrongValue() {
        ParseObject object = new ParseObject("Test");
        object.put("key", 1);

        assertNull(object.getParseGeoPoint("key"));
    }

    @Test
    public void testGetParsePolygon() {
        ParseObject object = new ParseObject("Test");
        List<ParseGeoPoint> points = new ArrayList<>();
        points.add(new ParseGeoPoint(0, 0));
        points.add(new ParseGeoPoint(0, 1));
        points.add(new ParseGeoPoint(1, 1));
        points.add(new ParseGeoPoint(1, 0));

        ParsePolygon polygon = new ParsePolygon(points);
        object.put("key", polygon);

        assertEquals(polygon, object.getParsePolygon("key"));
    }

    @Test
    public void testGetParsePolygonWithWrongValue() {
        ParseObject object = new ParseObject("Test");
        object.put("key", 1);

        assertNull(object.getParsePolygon("key"));
    }

    @Test
    public void testGetACL() {
        ParseObject object = new ParseObject("Test");
        ParseACL acl = new ParseACL();
        object.put("ACL", acl);

        assertEquals(acl, object.getACL());
    }

    @Test
    public void testGetACLWithSharedACL() {
        ParseObject object = new ParseObject("Test");
        ParseACL acl = new ParseACL();
        acl.setShared(true);
        acl.setPublicReadAccess(true);
        object.put("ACL", acl);

        ParseACL aclAgain = object.getACL();
        assertTrue(aclAgain.getPublicReadAccess());
    }

    @Test
    public void testGetACLWithNullValue() {
        ParseObject object = new ParseObject("Test");

        assertNull(object.getACL());
    }

    @Test
    public void testGetACLWithWrongValue() {
        ParseObject object = new ParseObject("Test");
        object.put("ACL", 1);

        thrown.expect(RuntimeException.class);
        thrown.expectMessage("only ACLs can be stored in the ACL key");

        object.getACL();
    }

    @Test
    public void testGetMap() throws Exception {
        ParseObject object = new ParseObject("Test");
        JSONObject json = new JSONObject();
        json.put("key", "value");
        json.put("keyAgain", "valueAgain");
        object.put("key", json);

        Map map = object.getMap("key");

        assertEquals(2, map.size());
        assertEquals("value", map.get("key"));
        assertEquals("valueAgain", map.get("keyAgain"));
    }

    @Test
    public void testGetMapWithWrongValue() {
        ParseObject object = new ParseObject("Test");
        object.put("key", 1);

        assertNull(object.getMap("key"));
    }

    @Test
    public void testGetParseUser() {
        ParseObject object = new ParseObject("Test");
        ParseUser user = mock(ParseUser.class);
        object.put("key", user);

        assertEquals(user, object.getParseUser("key"));
    }

    @Test
    public void testGetParseUserWithWrongValue() {
        ParseObject object = new ParseObject("Test");
        object.put("key", 1);

        assertNull(object.getParseUser("key"));
    }

    @Test
    public void testGetParseFile() {
        ParseObject object = new ParseObject("Test");
        ParseFile file = mock(ParseFile.class);
        object.put("key", file);

        assertEquals(file, object.getParseFile("key"));
    }

    @Test
    public void testGetParseFileWithWrongValue() {
        ParseObject object = new ParseObject("Test");
        object.put("key", 1);

        assertNull(object.getParseFile("key"));
    }

    @Test
    public void testGetDouble() {
        ParseObject object = new ParseObject("Test");
        object.put("key", 1.1);

        assertEquals(1.1, object.getDouble("key"), 0.00001);
    }

    //endregion

    //region testParcelable

    @Test
    public void testGetDoubleWithWrongValue() {
        ParseObject object = new ParseObject("Test");
        object.put("key", "str");

        assertEquals(0.0, object.getDouble("key"), 0.00001);
    }

    @Test
    public void testGetLong() {
        ParseObject object = new ParseObject("Test");
        object.put("key", 10L);

        assertEquals(10L, object.getLong("key"));
    }

    @Test
    public void testGetLongWithWrongValue() {
        ParseObject object = new ParseObject("Test");
        object.put("key", "str");

        assertEquals(0, object.getLong("key"));
    }

    @Test
    public void testParcelable() {
        ParseObject object = ParseObject.createWithoutData("Test", "objectId");
        object.isDeleted = true;
        object.put("long", 200L);
        object.put("double", 30D);
        object.put("int", 50);
        object.put("string", "test");
        object.put("date", new Date(200));
        object.put("null", JSONObject.NULL);
        // Collection
        object.put("collection", Arrays.asList("test1", "test2"));
        // Pointer
        ParseObject other = ParseObject.createWithoutData("Test", "otherId");
        object.put("pointer", other);
        // Map
        Map<String, Object> map = new HashMap<>();
        map.put("key1", "value");
        map.put("key2", 50);
        object.put("map", map);
        // Bytes
        byte[] bytes = new byte[2];
        object.put("bytes", bytes);
        // ACL
        ParseACL acl = new ParseACL();
        acl.setReadAccess("reader", true);
        object.setACL(acl);
        // Relation
        ParseObject related = ParseObject.createWithoutData("RelatedClass", "relatedId");
        ParseRelation<ParseObject> rel = new ParseRelation<>(object, "relation");
        rel.add(related);
        object.put("relation", rel);
        // File
        ParseFile file = new ParseFile(new ParseFile.State.Builder().url("fileUrl").build());
        object.put("file", file);
        // GeoPoint
        ParseGeoPoint point = new ParseGeoPoint(30d, 50d);
        object.put("point", point);

        Parcel parcel = Parcel.obtain();
        object.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        ParseObject newObject = ParseObject.CREATOR.createFromParcel(parcel);
        assertEquals(newObject.getClassName(), object.getClassName());
        assertEquals(newObject.isDeleted, object.isDeleted);
        assertEquals(newObject.hasChanges(), object.hasChanges());
        assertEquals(newObject.getLong("long"), object.getLong("long"));
        assertEquals(newObject.getDouble("double"), object.getDouble("double"), 0);
        assertEquals(newObject.getInt("int"), object.getInt("int"));
        assertEquals(newObject.getString("string"), object.getString("string"));
        assertEquals(newObject.getDate("date"), object.getDate("date"));
        assertEquals(newObject.get("null"), object.get("null"));
        assertEquals(newObject.getList("collection"), object.getList("collection"));
        assertEquals(newObject.getParseObject("pointer").getClassName(), other.getClassName());
        assertEquals(newObject.getParseObject("pointer").getObjectId(), other.getObjectId());
        assertEquals(newObject.getMap("map"), object.getMap("map"));
        assertEquals(newObject.getBytes("bytes").length, bytes.length);
        assertEquals(newObject.getACL().getReadAccess("reader"), acl.getReadAccess("reader"));
        ParseRelation newRel = newObject.getRelation("relation");
        assertEquals(newRel.getKey(), rel.getKey());
        assertEquals(newRel.getKnownObjects().size(), rel.getKnownObjects().size());
        newRel.hasKnownObject(related);
        assertEquals(newObject.getParseFile("file").getUrl(), object.getParseFile("file").getUrl());
        assertEquals(newObject.getParseGeoPoint("point").getLatitude(),
                object.getParseGeoPoint("point").getLatitude(), 0);
    }

    @Test
    public void testParcelWithCircularReference() {
        ParseObject parent = new ParseObject("Parent");
        ParseObject child = new ParseObject("Child");
        parent.setObjectId("parentId");
        parent.put("self", parent);
        child.setObjectId("childId");
        child.put("self", child);
        child.put("parent", parent);
        parent.put("child", child);

        Parcel parcel = Parcel.obtain();
        parent.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        parent = ParseObject.CREATOR.createFromParcel(parcel);
        assertEquals(parent.getObjectId(), "parentId");
        assertEquals(parent.getParseObject("self").getObjectId(), "parentId");
        child = parent.getParseObject("child");
        assertEquals(child.getObjectId(), "childId");
        assertEquals(child.getParseObject("self").getObjectId(), "childId");
        assertEquals(child.getParseObject("parent").getObjectId(), "parentId");
    }

    @Test
    public void testParcelWithCircularReferenceFromServer() {
        ParseObject parent = new ParseObject("Parent");
        ParseObject child = new ParseObject("Child");
        parent.setState(new ParseObject.State.Builder("Parent")
                .objectId("parentId")
                .put("self", parent)
                .put("child", child).build());
        parent.setObjectId("parentId");
        child.setState(new ParseObject.State.Builder("Child")
                .objectId("childId")
                .put("self", child)
                .put("parent", parent).build());

        Parcel parcel = Parcel.obtain();
        parent.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        parent = ParseObject.CREATOR.createFromParcel(parcel);
        assertEquals(parent.getObjectId(), "parentId");
        assertEquals(parent.getParseObject("self").getObjectId(), "parentId");
        child = parent.getParseObject("child");
        assertEquals(child.getObjectId(), "childId");
        assertEquals(child.getParseObject("self").getObjectId(), "childId");
        assertEquals(child.getParseObject("parent").getObjectId(), "parentId");
    }

    @Test
    public void testParcelWhileSaving() throws Exception {
        mockCurrentUserController();
        TaskCompletionSource<ParseObject.State> tcs = mockObjectControllerForSave();

        // Create multiple ParseOperationSets
        List<Task<Void>> tasks = new ArrayList<>();
        ParseObject object = new ParseObject("TestObject");
        object.setObjectId("id");
        object.put("key", "value");
        object.put("number", 5);
        tasks.add(object.saveInBackground());

        object.put("key", "newValue");
        object.increment("number", 6);
        tasks.add(object.saveInBackground());

        object.increment("number", -1);
        tasks.add(object.saveInBackground());

        // Ensure Log.w is called...
        assertTrue(object.hasOutstandingOperations());
        Parcel parcel = Parcel.obtain();
        object.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        ParseObject other = ParseObject.CREATOR.createFromParcel(parcel);
        assertTrue(other.isDirty("key"));
        assertTrue(other.isDirty("number"));
        assertEquals(other.getString("key"), "newValue");
        assertEquals(other.getNumber("number"), 10);
        // By design, when LDS is off, we assume that old operations failed even if
        // they are still running on the old instance.
        assertFalse(other.hasOutstandingOperations());

        // Force finish save operations on the old instance.
        tcs.setResult(null);
        ParseTaskUtils.wait(Task.whenAll(tasks));
    }

    //endregion

    //region testFailingDelete

    @Test
    public void testParcelWhileSavingWithLDSEnabled() throws Exception {
        mockCurrentUserController();
        TaskCompletionSource<ParseObject.State> tcs = mockObjectControllerForSave();

        ParseObject object = new ParseObject("TestObject");
        object.setObjectId("id");
        OfflineStore lds = mock(OfflineStore.class);
        when(lds.getObject("TestObject", "id")).thenReturn(object);
        Parse.setLocalDatastore(lds);

        object.put("key", "value");
        object.increment("number", 3);
        Task<Void> saveTask = object.saveInBackground();
        assertTrue(object.hasOutstandingOperations()); // Saving
        assertFalse(object.isDirty()); // Not dirty because it's saving

        Parcel parcel = Parcel.obtain();
        object.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        ParseObject other = ParseObject.CREATOR.createFromParcel(parcel);
        assertSame(object, other);
        assertTrue(other.hasOutstandingOperations()); // Still saving
        assertFalse(other.isDirty()); // Still not dirty
        assertEquals(other.getNumber("number"), 3);

        tcs.setResult(null);
        saveTask.waitForCompletion();
        Parse.setLocalDatastore(null);
    }

    //endregion

    //region testFailingSave

    @Test
    public void testParcelWhileDeleting() throws Exception {
        mockCurrentUserController();
        TaskCompletionSource<Void> tcs = mockObjectControllerForDelete();

        ParseObject object = new ParseObject("TestObject");
        object.setObjectId("id");
        Task<Void> deleteTask = object.deleteInBackground();

        // ensure Log.w is called..
        assertTrue(object.isDeleting);
        Parcel parcel = Parcel.obtain();
        object.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        ParseObject other = ParseObject.CREATOR.createFromParcel(parcel);
        // By design, when LDS is off, we assume that old operations failed even if
        // they are still running on the old instance.
        assertFalse(other.isDeleting);
        assertTrue(object.isDeleting);

        tcs.setResult(null);
        deleteTask.waitForCompletion();
        assertFalse(object.isDeleting);
        assertTrue(object.isDeleted);
    }

    //endregion

    @Test
    public void testParcelWhileDeletingWithLDSEnabled() throws Exception {
        mockCurrentUserController();
        TaskCompletionSource<Void> tcs = mockObjectControllerForDelete();

        ParseObject object = new ParseObject("TestObject");
        object.setObjectId("id");
        OfflineStore lds = mock(OfflineStore.class);
        when(lds.getObject("TestObject", "id")).thenReturn(object);
        Parse.setLocalDatastore(lds);
        Task<Void> deleteTask = object.deleteInBackground();

        assertTrue(object.isDeleting);
        Parcel parcel = Parcel.obtain();
        object.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        ParseObject other = ParseObject.CREATOR.createFromParcel(parcel);
        assertSame(object, other);
        assertTrue(other.isDeleting); // Still deleting

        tcs.setResult(null);
        deleteTask.waitForCompletion(); // complete deletion on original object.
        assertFalse(other.isDeleting);
        assertTrue(other.isDeleted);
        Parse.setLocalDatastore(null);
    }

    @Test
    public void testFailingDelete() throws Exception {

        ParseRESTCommand.server = new URL("https://api.parse.com/1");

        Parse.Configuration configuration = new Parse.Configuration.Builder(RuntimeEnvironment.application)
                .build();
        ParsePlugins plugins = mock(ParsePlugins.class);
        when(plugins.configuration()).thenReturn(configuration);
        when(plugins.applicationContext()).thenReturn(RuntimeEnvironment.application);
        ParsePlugins.set(plugins);

        JSONObject mockResponse = new JSONObject();
        mockResponse.put("code", 141);
        mockResponse.put("error", "Delete is not allowed");
        ParseHttpClient restClient =
                ParseTestUtils.mockParseHttpClientWithResponse(mockResponse, 400, "Bad Request");
        when(plugins.restClient()).thenReturn(restClient);

        ParseObject.State state = mock(ParseObject.State.class);
        when(state.className()).thenReturn("TestObject");
        when(state.objectId()).thenReturn("test_id");
        when(state.keySet()).thenReturn(Collections.singleton("key"));
        when(state.get("key")).thenReturn("data");
        ParseObject object = ParseObject.from(state);

        thrown.expect(ParseException.class);
        thrown.expectMessage("Delete is not allowed");

        object.delete();
    }

    @Test
    public void testFailingSave() throws Exception {

        ParseRESTCommand.server = new URL("https://api.parse.com/1");

        ParseObject.registerSubclass(ParseUser.class);

        Parse.Configuration configuration = new Parse.Configuration.Builder(RuntimeEnvironment.application)
                .build();
        ParsePlugins plugins = mock(ParsePlugins.class);
        when(plugins.configuration()).thenReturn(configuration);
        when(plugins.applicationContext()).thenReturn(RuntimeEnvironment.application);
        ParsePlugins.set(plugins);

        JSONObject mockResponse = new JSONObject();
        mockResponse.put("code", 141);
        mockResponse.put("error", "Save is not allowed");
        ParseHttpClient restClient =
                ParseTestUtils.mockParseHttpClientWithResponse(mockResponse, 400, "Bad Request");
        when(plugins.restClient()).thenReturn(restClient);

        ParseObject.State state = mock(ParseObject.State.class);
        when(state.className()).thenReturn("TestObject");
        when(state.objectId()).thenReturn("test_id");
        when(state.keySet()).thenReturn(Collections.singleton("key"));
        when(state.get("key")).thenReturn("data");
        ParseObject object = ParseObject.from(state);

        object.put("key", "other data");

        thrown.expect(ParseException.class);
        thrown.expectMessage("Save is not allowed");

        object.save();
    }
}

/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import bolts.Task;
import bolts.TaskCompletionSource;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ParseObjectTest {

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @After
  public void tearDown() {
    ParseCorePlugins.getInstance().reset();
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

    ParseFieldOperations.registerDefaultDecoders();

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

  @Test
  public void testFromJSONPayloadWithoutClassname() throws JSONException {
    JSONObject json = new JSONObject("{\"objectId\":\"TT1ZskATqS\"}");
    ParseObject parseObject = ParseObject.fromJSONPayload(json, ParseDecoder.get());
    assertNull(parseObject);
  }

  //region testRevert

  @Test
  public void testRevert() throws ParseException {
    List<Task<Void>> tasks = new ArrayList<>();

    // Mocked to let save work
    ParseCurrentUserController userController = mock(ParseCurrentUserController.class);
    when(userController.getAsync()).thenReturn(Task.<ParseUser>forResult(null));
    ParseCorePlugins.getInstance().registerCurrentUserController(userController);

    // Mocked to simulate in-flight save
    TaskCompletionSource<ParseObject.State> tcs = new TaskCompletionSource();
    ParseObjectController objectController = mock(ParseObjectController.class);
    when(objectController.saveAsync(
        any(ParseObject.State.class),
        any(ParseOperationSet.class),
        anyString(),
        any(ParseDecoder.class)))
        .thenReturn(tcs.getTask());
    ParseCorePlugins.getInstance().registerObjectController(objectController);

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
    ParseCurrentUserController userController = mock(ParseCurrentUserController.class);
    when(userController.getAsync()).thenReturn(Task.<ParseUser>forResult(null));
    ParseCorePlugins.getInstance().registerCurrentUserController(userController);

    // Mocked to simulate in-flight save
    TaskCompletionSource<ParseObject.State> tcs = new TaskCompletionSource();
    ParseObjectController objectController = mock(ParseObjectController.class);
    when(objectController.saveAsync(
        any(ParseObject.State.class),
        any(ParseOperationSet.class),
        anyString(),
        any(ParseDecoder.class)))
        .thenReturn(tcs.getTask());
    ParseCorePlugins.getInstance().registerObjectController(objectController);

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

  //endregion

  //region testGetter

  @Test( expected = IllegalStateException.class )
  public void testGetUnavailable() {
    ParseObject.State state = mock(ParseObject.State.class);
    when(state.className()).thenReturn("TestObject");
    when(state.isComplete()).thenReturn(false);

    ParseObject object = ParseObject.from(state);
    object.get("foo");
  }

  @Test
  public void testGetList() throws Exception {
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
  public void testGetListWithWrongValue() throws Exception {
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
  public void testGetJsonArrayWithWrongValue() throws Exception {
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
  public void testGetJsonObjectWithWrongValue() throws Exception {
    ParseObject object = new ParseObject("Test");
    object.put("key", 1);

    assertNull(object.getJSONObject("key"));
  }

  @Test
  public void testGetBoolean() throws Exception {
    ParseObject object = new ParseObject("Test");
    object.put("key", true);

    assertTrue(object.getBoolean("key"));
  }

  @Test
  public void testGetBooleanWithWrongValue() throws Exception {
    ParseObject object = new ParseObject("Test");
    object.put("key", 1);

    assertFalse(object.getBoolean("key"));
  }

  @Test
  public void testGetDate() throws Exception {
    ParseObject object = new ParseObject("Test");
    Date date = new Date();
    object.put("key", date);

    assertEquals(date, object.getDate("key"));
  }

  @Test
  public void testGetDateWithWrongValue() throws Exception {
    ParseObject object = new ParseObject("Test");
    object.put("key", 1);

    assertNull(object.getDate("key"));
  }

  @Test
  public void testGetParseGeoPoint() throws Exception {
    ParseObject object = new ParseObject("Test");
    ParseGeoPoint point = new ParseGeoPoint(10, 10);
    object.put("key", point);

    assertEquals(point, object.getParseGeoPoint("key"));
  }

  @Test
  public void testGetParseGeoPointWithWrongValue() throws Exception {
    ParseObject object = new ParseObject("Test");
    object.put("key", 1);

    assertNull(object.getParseGeoPoint("key"));
  }

  @Test
  public void testGetACL() throws Exception {
    ParseObject object = new ParseObject("Test");
    ParseACL acl = new ParseACL();
    object.put("ACL", acl);

    assertEquals(acl, object.getACL());
  }

  @Test
  public void testGetACLWithSharedACL() throws Exception {
    ParseObject object = new ParseObject("Test");
    ParseACL acl = new ParseACL();
    acl.setShared(true);
    acl.setPublicReadAccess(true);
    object.put("ACL", acl);

    ParseACL aclAgain = object.getACL();
    assertTrue(aclAgain.getPublicReadAccess());
  }

  @Test
  public void testGetACLWithNullValue() throws Exception {
    ParseObject object = new ParseObject("Test");

    assertNull(object.getACL());
  }

  @Test
  public void testGetACLWithWrongValue() throws Exception {
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
  public void testGetMapWithWrongValue() throws Exception {
    ParseObject object = new ParseObject("Test");
    object.put("key", 1);

    assertNull(object.getMap("key"));
  }

  @Test
  public void testGetParseUser() throws Exception {
    ParseObject object = new ParseObject("Test");
    ParseUser user = mock(ParseUser.class);
    object.put("key", user);

    assertEquals(user, object.getParseUser("key"));
  }

  @Test
  public void testGetParseUserWithWrongValue() throws Exception {
    ParseObject object = new ParseObject("Test");
    object.put("key", 1);

    assertNull(object.getParseUser("key"));
  }

  @Test
  public void testGetParseFile() throws Exception {
    ParseObject object = new ParseObject("Test");
    ParseFile file = mock(ParseFile.class);
    object.put("key", file);

    assertEquals(file, object.getParseFile("key"));
  }

  @Test
  public void testGetParseFileWithWrongValue() throws Exception {
    ParseObject object = new ParseObject("Test");
    object.put("key", 1);

    assertNull(object.getParseFile("key"));
  }

  @Test
  public void testGetDouble() throws Exception {
    ParseObject object = new ParseObject("Test");
    object.put("key", 1.1);

    assertEquals(1.1, object.getDouble("key"), 0.00001);
  }

  @Test
  public void testGetDoubleWithWrongValue() throws Exception {
    ParseObject object = new ParseObject("Test");
    object.put("key", "str");

    assertEquals(0.0, object.getDouble("key"), 0.00001);
  }

  @Test
  public void testGetLong() throws Exception {
    ParseObject object = new ParseObject("Test");
    object.put("key", 10L);

    assertEquals(10L, object.getLong("key"));
  }

  @Test
  public void testGetLongWithWrongValue() throws Exception {
    ParseObject object = new ParseObject("Test");
    object.put("key", "str");

    assertEquals(0, object.getLong("key"));
  }
  
  //endregion
}

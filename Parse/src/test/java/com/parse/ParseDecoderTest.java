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
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

// For android.util.Base64
@RunWith(RobolectricTestRunner.class)
@Config(constants = BuildConfig.class, sdk = TestHelper.ROBOLECTRIC_SDK_VERSION)
public class ParseDecoderTest extends ResetPluginsParseTest {

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Test
  public void testJSONArray() {
    JSONArray jsonArray = new JSONArray();
    List<String> list = (List<String>) ParseDecoder.get().decode(jsonArray);
    assertNotNull(list);
  }

  @Test
  public void testConvertJSONArrayToList() {
    JSONArray jsonArray = new JSONArray();
    jsonArray.put("Object1");

    JSONArray internalJSONArray = new JSONArray();
    internalJSONArray.put("Object2.1");
    internalJSONArray.put("Object2.2");
    jsonArray.put(internalJSONArray);

    List<Object> objects = ParseDecoder.get().convertJSONArrayToList(jsonArray);
    assertNotNull(objects);
    assertEquals(2, objects.size());
    assertEquals("Object1", objects.get(0));

    List<String> subObjects = (List<String>) objects.get(1);
    assertNotNull(subObjects);
    assertEquals(2, subObjects.size());
    assertEquals("Object2.1", subObjects.get(0));
    assertEquals("Object2.2", subObjects.get(1));
  }

  @Test
  public void testNonJSONObject() {
    Object obj = new Object();
    assertSame(obj, ParseDecoder.get().decode(obj));
  }

  @Test
  public void testParseFieldOperations() throws JSONException {
    JSONObject json = new JSONObject();
    json.put("__op", "Increment");
    json.put("amount", 1);
    ParseFieldOperations.registerDefaultDecoders();
    Object obj = ParseDecoder.get().decode(json);
    assertEquals("ParseIncrementOperation", obj.getClass().getSimpleName());
  }

  @Test
  public void testMap() throws JSONException {
    JSONObject json = new JSONObject();
    json.put("score", 3);
    json.put("age", 33);
    Map<String, Object> keyValueMap = (Map<String, Object>) ParseDecoder.get().decode(json);
    assertNotNull(keyValueMap);
  }

  @Test
  public void testConvertJSONObjectToMap() throws JSONException {
    JSONObject json = new JSONObject();
    json.put("Total Score", 20);

    JSONObject internalJSON = new JSONObject();
    internalJSON.put("Innings 1", 10);
    internalJSON.put("Innings 2", 10);
    json.put("Innings Scores", internalJSON);

    Map<String, Object> scoreMap = (Map<String, Object>) ParseDecoder.get().decode(json);
    assertNotNull(scoreMap);
    assertEquals(2, scoreMap.size());
    assertEquals(20, scoreMap.get("Total Score"));

    Map<String, Object> inningsMap = (Map<String, Object>) scoreMap.get("Innings Scores");
    assertNotNull(inningsMap);
    assertEquals(2, inningsMap.size());
    assertEquals(10, inningsMap.get("Innings 1"));
    assertEquals(10, inningsMap.get("Innings 2"));
  }

  @Test
  public void testDate() throws JSONException {
    JSONObject json = new JSONObject();
    json.put("__type", "Date");
    json.put("iso", "2011-08-21T18:02:52.249Z");
    Date date = (Date) ParseDecoder.get().decode(json);
    assertNotNull(date);
  }

  @Test
  public void testBytes() throws JSONException {
    JSONObject json = new JSONObject();
    json.put("__type", "Bytes");
    json.put("base64", "VGhpcyBpcyBhbiBlbmNvZGVkIHN0cmluZw==");
    byte[] byteArr = (byte[]) ParseDecoder.get().decode(json);
    assertEquals("This is an encoded string", new String(byteArr));
  }

  @Test
  public void testPointer() throws JSONException {
    JSONObject json = new JSONObject();
    json.put("__type", "Pointer");
    json.put("className", "GameScore");
    json.put("objectId", "Ed1nuqPvc");
    ParseObject pointerObject = (ParseObject) ParseDecoder.get().decode(json);
    assertNotNull(pointerObject);
    assertFalse(pointerObject.isDataAvailable());
  }

  @Test
  public void testFile() throws JSONException {
    JSONObject json = new JSONObject();
    json.put("__type", "File");
    json.put("name", "image-file.png");
    json.put("url", "http://folder/image-file.png");
    ParseFile parseFile = (ParseFile) ParseDecoder.get().decode(json);
    assertNotNull(parseFile);
  }

  @Test
  public void testGeoPointWithoutLongitude() throws JSONException {
    JSONObject json = new JSONObject();
    json.put("__type", "GeoPoint");
    json.put("latitude", 30);

    thrown.expect(RuntimeException.class);
    ParseDecoder.get().decode(json);
  }

  @Test
  public void testGeoPointWithoutLatitude() throws JSONException {

    JSONObject json = new JSONObject();
    json.put("__type", "GeoPoint");
    json.put("longitude", 30);

    thrown.expect(RuntimeException.class);
    ParseDecoder.get().decode(json);
  }

  @Test
  public void testGeoPoint() throws JSONException {
    JSONObject json = new JSONObject();
    json.put("__type", "GeoPoint");
    json.put("longitude", -20);
    json.put("latitude", 30);

    ParseGeoPoint parseGeoPoint = (ParseGeoPoint) ParseDecoder.get().decode(json);
    assertNotNull(parseGeoPoint);
    final double DELTA = 0.00001;
    assertEquals(-20, parseGeoPoint.getLongitude(), DELTA);
    assertEquals(30, parseGeoPoint.getLatitude(), DELTA);
  }

  @Test
  public void testParseObject() throws JSONException {
    JSONObject json = new JSONObject();
    json.put("__type", "Object");
    json.put("className", "GameScore");
    json.put("createdAt", "2015-06-22T21:23:41.733Z");
    json.put("objectId", "TT1ZskATqS");
    json.put("updatedAt", "2015-06-22T22:06:18.104Z");
    ParseObject parseObject = (ParseObject) ParseDecoder.get().decode(json);
    assertNotNull(parseObject);
  }

  @Test
  public void testIncludedParseObject() throws JSONException {
    JSONObject json = new JSONObject();
    json.put("__type", "Object");
    json.put("className", "GameScore");
    json.put("createdAt", "2015-06-22T21:23:41.733Z");
    json.put("objectId", "TT1ZskATqS");
    json.put("updatedAt", "2015-06-22T22:06:18.104Z");

    JSONObject child = new JSONObject();
    child.put("__type", "Object");
    child.put("className", "GameScore");
    child.put("createdAt", "2015-06-22T21:23:41.733Z");
    child.put("objectId", "TT1ZskATqR");
    child.put("updatedAt", "2015-06-22T22:06:18.104Z");

    json.put("child", child);
    ParseObject parseObject = (ParseObject) ParseDecoder.get().decode(json);
    assertNotNull(parseObject.getParseObject("child"));
  }

  @Test
  public void testCompleteness() throws JSONException {
    JSONObject json = new JSONObject();
    json.put("__type", "Object");
    json.put("className", "GameScore");
    json.put("createdAt", "2015-06-22T21:23:41.733Z");
    json.put("objectId", "TT1ZskATqS");
    json.put("updatedAt", "2015-06-22T22:06:18.104Z");
    json.put("foo", "foo");
    json.put("bar", "bar");
    ParseObject parseObject = (ParseObject) ParseDecoder.get().decode(json);
    assertTrue(parseObject.isDataAvailable());

    JSONArray arr = new JSONArray("[\"foo\"]");
    json.put("__selectedKeys", arr);
    parseObject = (ParseObject) ParseDecoder.get().decode(json);
    assertFalse(parseObject.isDataAvailable());
  }

  @Test
  public void testCompletenessOfIncludedParseObject() throws JSONException {
    JSONObject json = new JSONObject();
    json.put("__type", "Object");
    json.put("className", "GameScore");
    json.put("createdAt", "2015-06-22T21:23:41.733Z");
    json.put("objectId", "TT1ZskATqS");
    json.put("updatedAt", "2015-06-22T22:06:18.104Z");

    JSONObject child = new JSONObject();
    child.put("__type", "Object");
    child.put("className", "GameScore");
    child.put("createdAt", "2015-06-22T21:23:41.733Z");
    child.put("objectId", "TT1ZskATqR");
    child.put("updatedAt", "2015-06-22T22:06:18.104Z");
    child.put("bar", "child bar");

    JSONArray arr = new JSONArray("[\"foo.bar\"]");
    json.put("foo", child);
    json.put("__selectedKeys", arr);
    ParseObject parentObject = (ParseObject) ParseDecoder.get().decode(json);
    assertFalse(parentObject.isDataAvailable());
    assertTrue(parentObject.isDataAvailable("foo"));
    ParseObject childObject = parentObject.getParseObject("foo");
    assertFalse(childObject.isDataAvailable());
    assertTrue(childObject.isDataAvailable("bar"));
  }

  @Test
  public void testRelation() throws JSONException {
    JSONObject json = new JSONObject();
    json.put("__type", "Relation");
    json.put("className", "Player");
    json.put("objectId", "TT1ZskATqS");
    ParseRelation<ParseObject> parseRelation =
        (ParseRelation<ParseObject>) ParseDecoder.get().decode(json);
    assertNotNull(parseRelation);
  }

  @Test
  public void testOfflineObject() throws JSONException {
    thrown.expect(RuntimeException.class);
    thrown.expectMessage("An unexpected offline pointer was encountered.");


    JSONObject json = new JSONObject();
    json.put("__type", "OfflineObject");
    ParseDecoder.get().decode(json);
  }

  @Test
  public void testMisc() throws JSONException {
    JSONObject json = new JSONObject();
    json.put("__type", "TestType");
    assertNull(ParseDecoder.get().decode(json));
  }
}

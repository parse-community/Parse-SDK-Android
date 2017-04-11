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
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.skyscreamer.jsonassert.JSONCompareMode;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import bolts.Task;

import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.skyscreamer.jsonassert.JSONAssert.assertEquals;


// For android.os.Looper
@RunWith(RobolectricTestRunner.class)
@Config(constants = BuildConfig.class, sdk = TestHelper.ROBOLECTRIC_SDK_VERSION)
public class ParseConfigTest {

  @Before
  public void setUp() {
    ParseTestUtils.setTestParseUser();
  }

  @After
  public void tearDown() {
    ParseCorePlugins.getInstance().reset();
  }

  //region testConstructor

  @Test
  public void testDefaultConstructor() {
    ParseConfig config = new ParseConfig();

    assertEquals(0, config.params.size());
  }

  @Test
  public void testDecodeWithValidJsonObject() throws Exception {
    final Map<String, Object> params = new HashMap<>();
    params.put("string", "value");
    JSONObject json = new JSONObject();
    json.put("params", NoObjectsEncoder.get().encode(params));

    ParseConfig config = ParseConfig.decode(json, ParseDecoder.get());
    assertEquals(1, config.params.size());
    assertEquals("value", config.params.get("string"));
  }

  @Test(expected = RuntimeException.class)
  public void testDecodeWithInvalidJsonObject() throws Exception {
    final Map<String, Object> params = new HashMap<>();
    params.put("string", "value");
    JSONObject json = new JSONObject();
    json.put("error", NoObjectsEncoder.get().encode(params));

    ParseConfig.decode(json, ParseDecoder.get());
  }

  //endregion

  //region testGetInBackground

  @Test
  public void testGetInBackgroundSuccess() throws Exception {
    final Map<String, Object> params = new HashMap<>();
    params.put("string", "value");

    ParseConfig config = new ParseConfig(params);
    ParseConfigController controller = mockParseConfigControllerWithResponse(config);
    ParseCorePlugins.getInstance().registerConfigController(controller);

    Task<ParseConfig> configTask = ParseConfig.getInBackground();
    ParseTaskUtils.wait(configTask);
    ParseConfig configAgain = configTask.getResult();

    verify(controller, times(1)).getAsync(anyString());
    assertEquals(1, configAgain.params.size());
    assertEquals("value", configAgain.params.get("string"));
  }

  @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
  @Test
  public void testGetInBackgroundFail() throws Exception {
    ParseException exception = new ParseException(ParseException.CONNECTION_FAILED, "error");
    ParseConfigController controller = mockParseConfigControllerWithException(exception);
    ParseCorePlugins.getInstance().registerConfigController(controller);

    Task<ParseConfig> configTask = ParseConfig.getInBackground();
    configTask.waitForCompletion();

    verify(controller, times(1)).getAsync(anyString());
    assertThat(configTask.getError(), instanceOf(ParseException.class));
    assertEquals(ParseException.CONNECTION_FAILED,
        ((ParseException) configTask.getError()).getCode());
    assertEquals("error", configTask.getError().getMessage());
  }

  @Test
  public void testGetInBackgroundWithCallbackSuccess() throws Exception {
    final Map<String, Object> params = new HashMap<>();
    params.put("string", "value");

    ParseConfig config = new ParseConfig(params);
    ParseConfigController controller = mockParseConfigControllerWithResponse(config);
    ParseCorePlugins.getInstance().registerConfigController(controller);

    final Semaphore done = new Semaphore(0);
    ParseConfig.getInBackground(new ConfigCallback() {
      @Override
      public void done(ParseConfig config, ParseException e) {
        assertEquals(1, config.params.size());
        assertEquals("value", config.params.get("string"));
        done.release();
      }
    });

    // Make sure the callback is called
    assertTrue(done.tryAcquire(1, 10, TimeUnit.SECONDS));
    verify(controller, times(1)).getAsync(anyString());
  }

  @Test
  public void testGetInBackgroundWithCallbackFail() throws Exception {
    ParseException exception = new ParseException(ParseException.CONNECTION_FAILED, "error");
    ParseConfigController controller = mockParseConfigControllerWithException(exception);
    ParseCorePlugins.getInstance().registerConfigController(controller);

    final Semaphore done = new Semaphore(0);
    ParseConfig.getInBackground(new ConfigCallback() {
      @Override
      public void done(ParseConfig config, ParseException e) {
        assertEquals(ParseException.CONNECTION_FAILED, e.getCode());
        assertEquals("error", e.getMessage());
        done.release();
      }
    });

    // Make sure the callback is called
    assertTrue(done.tryAcquire(1, 10, TimeUnit.SECONDS));
    verify(controller, times(1)).getAsync(anyString());
  }

  @Test
  public void testGetSyncSuccess() throws Exception {
    final Map<String, Object> params = new HashMap<>();
    params.put("string", "value");

    ParseConfig config = new ParseConfig(params);
    ParseConfigController controller = mockParseConfigControllerWithResponse(config);
    ParseCorePlugins.getInstance().registerConfigController(controller);

    ParseConfig configAgain = ParseConfig.get();

    verify(controller, times(1)).getAsync(anyString());
    assertEquals(1, configAgain.params.size());
    assertEquals("value", configAgain.params.get("string"));
  }

  @Test
  public void testGetSyncFail() {
    ParseException exception = new ParseException(ParseException.CONNECTION_FAILED, "error");
    ParseConfigController controller = mockParseConfigControllerWithException(exception);
    ParseCorePlugins.getInstance().registerConfigController(controller);

    try {
      ParseConfig.get();
      fail("Should throw an exception");
    } catch (ParseException e) {
      verify(controller, times(1)).getAsync(anyString());
      assertEquals(ParseException.CONNECTION_FAILED, e.getCode());
      assertEquals("error", e.getMessage());
    }
  }

  //endregion

  //region testGetCurrentConfig

  @Test
  public void testGetCurrentConfigSuccess() throws Exception {
    final Map<String, Object> params = new HashMap<>();
    params.put("string", "value");

    ParseConfig config = new ParseConfig(params);
    ParseConfigController controller = new ParseConfigController(mock(ParseHttpClient.class),
        mockParseCurrentConfigControllerWithResponse(config));
    ParseCorePlugins.getInstance().registerConfigController(controller);

    ParseConfig configAgain = ParseConfig.getCurrentConfig();
    assertSame(config, configAgain);
  }

  @Test
  public void testGetCurrentConfigFail() throws Exception {
    ParseException exception = new ParseException(ParseException.CONNECTION_FAILED, "error");
    ParseConfigController controller = new ParseConfigController(mock(ParseHttpClient.class),
        mockParseCurrentConfigControllerWithException(exception));
    ParseCorePlugins.getInstance().registerConfigController(controller);

    ParseConfig configAgain = ParseConfig.getCurrentConfig();
    // Make sure we get an empty ParseConfig
    assertEquals(0, configAgain.getParams().size());
  }

  //endregion

  //region testGetBoolean

  @Test
  public void testGetBooleanKeyExist() throws Exception {
    final Map<String, Object> params = new HashMap<>();
    params.put("key", true);

    ParseConfig config = new ParseConfig(params);
    assertTrue(config.getBoolean("key"));
    assertTrue(config.getBoolean("key", false));
  }

  @Test
  public void testGetBooleanKeyNotExist() throws Exception {
    final Map<String, Object> params = new HashMap<>();
    params.put("key", true);

    ParseConfig config = new ParseConfig(params);
    assertFalse(config.getBoolean("wrongKey"));
    assertFalse(config.getBoolean("wrongKey", false));
  }

  @Test
  public void testGetBooleanKeyExistValueNotBoolean() throws Exception {
    final Map<String, Object> params = new HashMap<>();
    params.put("key", 1);

    ParseConfig config = new ParseConfig(params);
    assertFalse(config.getBoolean("key"));
    assertFalse(config.getBoolean("key", false));
  }

  //endregion

  //region testGetInt

  @Test
  public void testGetIntKeyExist() throws Exception {
    final Map<String, Object> params = new HashMap<>();
    params.put("key", 998);

    ParseConfig config = new ParseConfig(params);
    assertEquals(config.getInt("key"), 998);
    assertEquals(998, config.getInt("key", 100));
  }

  @Test
  public void testGetIntKeyNotExist() throws Exception {
    final Map<String, Object> params = new HashMap<>();
    params.put("key", 998);

    ParseConfig config = new ParseConfig(params);
    assertEquals(config.getInt("wrongKey"), 0);
    assertEquals(100, config.getInt("wrongKey", 100));
  }

  //endregion

  //region testGetDouble

  @Test
  public void testGetDoubleKeyExist() throws Exception {
    final Map<String, Object> params = new HashMap<>();
    params.put("key", 998.1);

    ParseConfig config = new ParseConfig(params);
    assertEquals(config.getDouble("key"), 998.1, 0.0001);
    assertEquals(998.1, config.getDouble("key", 100.1), 0.0001);
  }

  @Test
  public void testGetDoubleKeyNotExist() throws Exception {
    final Map<String, Object> params = new HashMap<>();
    params.put("key", 998.1);

    ParseConfig config = new ParseConfig(params);
    assertEquals(config.getDouble("wrongKey"), 0.0, 0.0001);
    assertEquals(100.1, config.getDouble("wrongKey", 100.1), 0.0001);
  }

  //endregion

  //region testGetLong

  @Test
  public void testGetLongKeyExist() throws Exception {
    final Map<String, Object> params = new HashMap<>();
    params.put("key", (long)998);

    ParseConfig config = new ParseConfig(params);
    assertEquals(config.getLong("key"), (long)998);
    assertEquals((long)998, config.getLong("key", (long) 100));
  }

  @Test
  public void testGetLongKeyNotExist() throws Exception {
    final Map<String, Object> params = new HashMap<>();
    params.put("key", (long)998);

    ParseConfig config = new ParseConfig(params);
    assertEquals(config.getLong("wrongKey"), (long)0);
    assertEquals((long)100, config.getLong("wrongKey", (long) 100));
  }

  //endregion

  //region testGet

  @Test
  public void testGetKeyExist() throws Exception {
    final Map<String, Object> params = new HashMap<>();
    params.put("key", "value");

    ParseConfig config = new ParseConfig(params);
    assertEquals(config.get("key"), "value");
    assertEquals("value", config.get("key", "haha"));
  }

  @Test
  public void testGetKeyNotExist() throws Exception {
    final Map<String, Object> params = new HashMap<>();
    params.put("key", "value");

    ParseConfig config = new ParseConfig(params);
    assertNull(config.get("wrongKey"));
    assertEquals("haha", config.get("wrongKey", "haha"));
  }

  @Test
  public void testGetKeyExistValueNull() throws Exception {
    final Map<String, Object> params = new HashMap<>();
    params.put("key", JSONObject.NULL);
    params.put("keyAgain", null);

    ParseConfig config = new ParseConfig(params);
    assertNull(config.get("key"));
    assertNull(config.get("key", "haha"));
    assertNull(config.get("keyAgain"));
    assertNull(config.get("keyAgain", "haha"));
  }

  //endregion

  //region testGetString

  @Test
  public void testGetStringKeyExist() throws Exception {
    final Map<String, Object> params = new HashMap<>();
    params.put("key", "value");

    ParseConfig config = new ParseConfig(params);
    assertEquals(config.getString("key"), "value");
    assertEquals("value", config.getString("key", "haha"));
  }

  @Test
  public void testGetStringKeyNotExist() throws Exception {
    final Map<String, Object> params = new HashMap<>();
    params.put("key", "value");

    ParseConfig config = new ParseConfig(params);
    assertNull(config.getString("wrongKey"));
    assertEquals("haha", config.getString("wrongKey", "haha"));
  }

  @Test
  public void testGetStringKeyExistValueNotString() throws Exception {
    final Map<String, Object> params = new HashMap<>();
    params.put("key", 1);

    ParseConfig config = new ParseConfig(params);
    assertNull(config.getString("key"));
    assertEquals("haha", config.getString("key", "haha"));
  }

  @Test
  public void testGetStringKeyExistValueNull() throws Exception {
    final Map<String, Object> params = new HashMap<>();
    params.put("key", JSONObject.NULL);
    params.put("keyAgain", null);

    ParseConfig config = new ParseConfig(params);
    assertNull(config.getString("key"));
    assertNull(config.getString("key", "haha"));
    assertNull(config.getString("keyAgain"));
    assertNull(config.getString("keyAgain", "haha"));
  }

  //endregion

  //region testGetDate

  @Test
  public void testGetDateKeyExist() throws Exception {
    final Date date = new Date();
    date.setTime(10);
    Date dateAgain = new Date();
    dateAgain.setTime(20);
    final Map<String, Object> params = new HashMap<>();
    params.put("key", date);

    ParseConfig config = new ParseConfig(params);
    assertEquals(date.getTime(), config.getDate("key").getTime());
    assertEquals(date.getTime(), config.getDate("key", dateAgain).getTime());
  }

  @Test
  public void testGetDateKeyNotExist() throws Exception {
    final Date date = new Date();
    date.setTime(10);
    Date dateAgain = new Date();
    dateAgain.setTime(10);
    final Map<String, Object> params = new HashMap<>();
    params.put("key", date);

    ParseConfig config = new ParseConfig(params);
    assertNull(config.getDate("wrongKey"));
    assertSame(dateAgain, config.getDate("wrongKey", dateAgain));
  }

  @Test
  public void testGetDateKeyExistValueNotDate() throws Exception {
    Date date = new Date();
    date.setTime(20);
    final Map<String, Object> params = new HashMap<>();
    params.put("key", 1);

    ParseConfig config = new ParseConfig(params);
    assertNull(config.getDate("key"));
    assertSame(date, config.getDate("key", date));
  }

  @Test
  public void testGetDateKeyExistValueNull() throws Exception {
    Date date = new Date();
    date.setTime(20);
    final Map<String, Object> params = new HashMap<>();
    params.put("key", JSONObject.NULL);
    params.put("keyAgain", null);

    ParseConfig config = new ParseConfig(params);
    assertNull(config.getDate("key"));
    assertNull(config.getDate("key", date));
    assertNull(config.getDate("keyAgain"));
    assertNull(config.getDate("keyAgain", date));
  }

  //endregion

  //region testGetList

  @Test
  public void testGetListKeyExist() throws Exception {
    final List<String> list = new ArrayList<>();
    list.add("foo");
    list.add("bar");
    list.add("baz");
    final List<String> listAgain = new ArrayList<>();
    list.add("fooAgain");
    list.add("barAgain");
    list.add("bazAgain");
    final Map<String, Object> params = new HashMap<>();
    params.put("key", list);

    ParseConfig config = new ParseConfig(params);
    assertArrayEquals(list.toArray(), config.getList("key").toArray());
    assertArrayEquals(list.toArray(), config.getList("key", listAgain).toArray());
  }

  @Test
  public void testGetListKeyNotExist() throws Exception {
    final List<String> list = new ArrayList<>();
    list.add("foo");
    list.add("bar");
    list.add("baz");
    final List<String> listAgain = new ArrayList<>();
    list.add("fooAgain");
    list.add("barAgain");
    list.add("bazAgain");
    final Map<String, Object> params = new HashMap<>();
    params.put("key", list);

    ParseConfig config = new ParseConfig(params);
    assertNull(config.getList("wrongKey"));
    assertSame(listAgain, config.getList("wrongKey", listAgain));
  }

  @Test
  public void testGetListKeyExistValueNotList() throws Exception {
    final List<String> list = new ArrayList<>();
    list.add("foo");
    list.add("bar");
    list.add("baz");
    final Map<String, Object> params = new HashMap<>();
    params.put("key", 1);

    ParseConfig config = new ParseConfig(params);
    assertNull(config.getList("key"));
    assertSame(list, config.getList("key", list));
  }

  @Test
  public void testGetListKeyExistValueNull() throws Exception {
    final List<String> list = new ArrayList<>();
    list.add("fooAgain");
    list.add("barAgain");
    list.add("bazAgain");
    final Map<String, Object> params = new HashMap<>();
    params.put("key", JSONObject.NULL);
    params.put("keyAgain", null);

    ParseConfig config = new ParseConfig(params);
    assertNull(config.getList("key"));
    assertNull(config.getList("key", list));
    assertNull(config.getList("keyAgain"));
    assertNull(config.getList("keyAgain", list));
  }

  //endregion

  //region testGetNumber

  @Test
  public void testGetNumberKeyExist() throws Exception {
    final Number number = 1;
    Number numberAgain = 2;
    final Map<String, Object> params = new HashMap<>();
    params.put("key", number);

    ParseConfig config = new ParseConfig(params);
    assertEquals(number, config.getNumber("key"));
    assertEquals(number, config.getNumber("key", numberAgain));
  }

  @Test
  public void testGetNumberKeyNotExist() throws Exception {
    final Number number = 1;
    Number numberAgain = 2;
    final Map<String, Object> params = new HashMap<>();
    params.put("key", number);

    ParseConfig config = new ParseConfig(params);
    assertNull(config.getNumber("wrongKey"));
    assertSame(numberAgain, config.getNumber("wrongKey", numberAgain));
  }

  @Test
  public void testGetNumberKeyExistValueNotNumber() throws Exception {
    Number number = 2;
    final Map<String, Object> params = new HashMap<>();
    params.put("key", new ArrayList<String>());

    ParseConfig config = new ParseConfig(params);
    assertNull(config.getNumber("key"));
    assertSame(number, config.getNumber("key", number));
  }

  @Test
  public void testGetNumberKeyExistValueNull() throws Exception {
    Number number = 2;
    final Map<String, Object> params = new HashMap<>();
    params.put("key", JSONObject.NULL);
    params.put("keyAgain", null);

    ParseConfig config = new ParseConfig(params);
    assertNull(config.getNumber("key"));
    assertNull(config.getNumber("key", number));
    assertNull(config.getNumber("keyAgain"));
    assertNull(config.getNumber("keyAgain", number));
  }

  //endregion

  //region testGetMap

  @Test
  public void testGetMapKeyExist() throws Exception {
    final Map<String, Object> map = new HashMap<>();
    map.put("first", "foo");
    map.put("second", "bar");
    map.put("third", "baz");
    Map<String, Object> mapAgain = new HashMap<>();
    mapAgain.put("firstAgain", "fooAgain");
    mapAgain.put("secondAgain", "barAgain");
    mapAgain.put("thirdAgain", "bazAgain");
    final Map<String, Object> params = new HashMap<>();
    params.put("key", map);

    ParseConfig config = new ParseConfig(params);
    Map<String, Object> mapConfig = config.getMap("key");
    assertEquals(3, mapConfig.size());
    assertEquals("foo", mapConfig.get("first"));
    assertEquals("bar", mapConfig.get("second"));
    assertEquals("baz", mapConfig.get("third"));
    assertSame(mapConfig, config.getMap("key", mapAgain));
  }

  @Test
  public void testGetMapKeyNotExist() throws Exception {
    final Map<String, Object> map = new HashMap<>();
    map.put("first", "foo");
    map.put("second", "bar");
    map.put("third", "baz");
    Map<String, Object> mapAgain = new HashMap<>();
    mapAgain.put("firstAgain", "fooAgain");
    mapAgain.put("secondAgain", "barAgain");
    mapAgain.put("thirdAgain", "bazAgain");
    final Map<String, Object> params = new HashMap<>();
    params.put("key", map);

    ParseConfig config = new ParseConfig(params);
    assertNull(config.getMap("wrongKey"));
    assertSame(mapAgain, config.getMap("wrongKey", mapAgain));
  }

  @Test
  public void testGetMapKeyExistValueNotMap() throws Exception {
    Map<String, Object> map = new HashMap<>();
    map.put("firstAgain", "fooAgain");
    map.put("secondAgain", "barAgain");
    map.put("thirdAgain", "bazAgain");
    final Map<String, Object> params = new HashMap<>();
    params.put("key", 1);

    ParseConfig config = new ParseConfig(params);
    assertNull(config.getMap("key"));
    assertSame(map, config.getMap("key", map));
  }

  @Test
  public void testGetMapKeyExistValueNull() throws Exception {
    Map<String, Object> map = new HashMap<>();
    map.put("firstAgain", "fooAgain");
    map.put("secondAgain", "barAgain");
    map.put("thirdAgain", "bazAgain");
    final Map<String, Object> params = new HashMap<>();
    params.put("key", JSONObject.NULL);
    params.put("keyAgain", null);

    ParseConfig config = new ParseConfig(params);
    assertNull(config.getMap("key"));
    assertNull(config.getMap("key", map));
    assertNull(config.getMap("keyAgain"));
    assertNull(config.getMap("keyAgain", map));
  }

  //endregion

  //region testGetJsonObject

  @Test
  public void testGetJsonObjectKeyExist() throws Exception {
    final Map<String, String> value = new HashMap<>();
    value.put("key", "value");
    final Map<String, Object> params = new HashMap<>();
    params.put("key", value);

    final JSONObject json = new JSONObject(value);

    ParseConfig config = new ParseConfig(params);
    assertEquals(json, config.getJSONObject("key"), JSONCompareMode.NON_EXTENSIBLE);
    assertEquals(json, config.getJSONObject("key", new JSONObject()),
        JSONCompareMode.NON_EXTENSIBLE);
  }

  @Test
  public void testGetJsonObjectKeyNotExist() throws Exception {
    final JSONObject json = new JSONObject();
    json.put("key", "value");
    final JSONObject jsonAgain = new JSONObject();
    jsonAgain.put("keyAgain", "valueAgain");
    final Map<String, Object> params;
    params = new HashMap<>();
    params.put("key", json);

    ParseConfig config = new ParseConfig(params);
    assertNull(config.getJSONObject("wrongKey"));
    //TODO(mengyan) ParseConfig.getJSONObject should return jsonAgain, but due to the error in
    // ParseConfig.getJsonObject, this returns null right now. Revise when we correct the logic
    // for ParseConfig.getJsonObject.
    assertNull(config.getJSONObject("wrongKey", jsonAgain));
  }

  @Test
  public void testGetJsonObjectKeyExistValueNotJsonObject() throws Exception {
    final JSONObject json = new JSONObject();
    json.put("key", "value");
    final Map<String, Object> params = new HashMap<>();
    params.put("key", 1);

    ParseConfig config = new ParseConfig(params);
    assertNull(config.getJSONObject("key"));
    //TODO(mengyan) ParseConfig.getJSONObject should return json, but due to the error in
    // ParseConfig.getJsonObject, this returns null right now. Revise when we correct the logic
    // for ParseConfig.getJsonObject.
    assertNull(config.getJSONObject("key", json));
  }

  @Test
  public void testGetJsonObjectKeyExistValueNull() throws Exception {
    final JSONObject json = new JSONObject();
    json.put("key", "value");
    final Map<String, Object> params = new HashMap<>();
    params.put("key", JSONObject.NULL);
    params.put("keyAgain", null);

    ParseConfig config = new ParseConfig(params);
    assertNull(config.getJSONObject("key"));
    assertNull(config.getJSONObject("key", json));
    assertNull(config.getJSONObject("keyAgain"));
    assertNull(config.getJSONObject("keyAgain", json));
  }

  //endregion

  //region testGetJsonArray

  @Test
  public void testGetJsonArrayKeyExist() throws Exception {
    final Map<String, String> map = new HashMap<>();
    map.put("key", "value");
    final Map<String, String> mapAgain = new HashMap<>();
    mapAgain.put("keyAgain", "valueAgain");
    final List<Map<String, String>> value = new ArrayList<>();
    value.add(map);
    value.add(mapAgain);
    final Map<String, Object> params = new HashMap<>();
    params.put("key", value);

    JSONArray jsonArray = new JSONArray(value);

    ParseConfig config = new ParseConfig(params);
    assertEquals(jsonArray, config.getJSONArray("key"), JSONCompareMode.NON_EXTENSIBLE);
    assertEquals(jsonArray, config.getJSONArray("key", new JSONArray()),
        JSONCompareMode.NON_EXTENSIBLE);
  }

  @Test
  public void testGetJsonArrayKeyNotExist() throws Exception {
    final JSONObject json = new JSONObject();
    json.put("key", "value");
    final JSONObject jsonAgain = new JSONObject();
    jsonAgain.put("keyAgain", "valueAgain");
    final JSONArray jsonArray = new JSONArray();
    jsonArray.put(0, json);
    jsonArray.put(1, jsonAgain);
    final JSONArray jsonArrayAgain = new JSONArray();
    jsonArray.put(0, jsonAgain);
    jsonArray.put(1, json);
    final Map<String, Object> params = new HashMap<>();
    params.put("key", jsonArray);

    ParseConfig config = new ParseConfig(params);
    assertNull(config.getJSONArray("wrongKey"));
    //TODO(mengyan) ParseConfig.getJSONArray should return default jsonArrayAgain, but due to the
    // error in ParseConfig.getJSONArray, this returns null right now. Revise when we correct the
    // logic for ParseConfig.getJSONArray.
    assertNull(config.getJSONArray("wrongKey", jsonArrayAgain));
  }

  @Test
  public void testGetJsonArrayKeyExistValueNotJsonObject() throws Exception {
    final JSONObject json = new JSONObject();
    json.put("key", "value");
    final JSONObject jsonAgain = new JSONObject();
    jsonAgain.put("keyAgain", "valueAgain");
    final JSONArray jsonArray = new JSONArray();
    jsonArray.put(0, json);
    jsonArray.put(1, jsonAgain);
    final Map<String, Object> params = new HashMap<>();
    params.put("key", 1);

    ParseConfig config = new ParseConfig(params);
    assertNull(config.getJSONArray("key"));
    //TODO(mengyan) ParseConfig.getJSONArray should return default jsonArray, but due to the
    // error in ParseConfig.getJSONArray, this returns null right now. Revise when we correct the
    // logic for ParseConfig.getJSONArray.
    assertNull(config.getJSONArray("key", jsonArray));
  }

  @Test
  public void testGetJsonArrayKeyExistValueNull() throws Exception {
    final JSONObject json = new JSONObject();
    json.put("key", "value");
    final JSONObject jsonAgain = new JSONObject();
    jsonAgain.put("keyAgain", "valueAgain");
    final JSONArray jsonArray = new JSONArray();
    jsonArray.put(0, json);
    jsonArray.put(1, jsonAgain);
    final Map<String, Object> params = new HashMap<>();
    params.put("key", null);

    ParseConfig config = new ParseConfig(params);
    assertNull(config.getJSONArray("key"));
    assertNull(config.getJSONArray("key", jsonArray));
    assertNull(config.getJSONArray("keyAgain"));
    assertNull(config.getJSONArray("keyAgain", jsonArray));
  }

  //endregion

  //region testGetParseGeoPoint

  @Test
  public void testGetParseGeoPointKeyExist() throws Exception {
    final ParseGeoPoint geoPoint = new ParseGeoPoint(44.484, 26.029);
    ParseGeoPoint geoPointAgain = new ParseGeoPoint(45.484, 27.029);
    final Map<String, Object> params = new HashMap<>();
    params.put("key", geoPoint);

    ParseConfig config = new ParseConfig(params);
    ParseGeoPoint geoPointConfig = config.getParseGeoPoint("key");
    assertEquals(geoPoint.getLongitude(), geoPointConfig.getLongitude(), 0.0001);
    assertEquals(geoPoint.getLatitude(), geoPointConfig.getLatitude(), 0.0001);
    assertSame(geoPointConfig, config.getParseGeoPoint("key", geoPointAgain));
  }

  @Test
  public void testGetParseGeoPointKeyNotExist() throws Exception {
    final ParseGeoPoint geoPoint = new ParseGeoPoint(44.484, 26.029);
    ParseGeoPoint geoPointAgain = new ParseGeoPoint(45.484, 27.029);
    final Map<String, Object> params = new HashMap<>();
    params.put("key", geoPoint);

    ParseConfig config = new ParseConfig(params);
    assertNull(config.getParseGeoPoint("wrongKey"));
    assertSame(geoPointAgain, config.getParseGeoPoint("wrongKey", geoPointAgain));
  }

  @Test
  public void testGetParseGeoPointKeyExistValueNotParseGeoPoint() throws Exception {
    ParseGeoPoint geoPoint = new ParseGeoPoint(45.484, 27.029);
    final Map<String, Object> params = new HashMap<>();
    params.put("key", 1);

    ParseConfig config = new ParseConfig(params);
    assertNull(config.getParseGeoPoint("key"));
    assertSame(geoPoint, config.getParseGeoPoint("key", geoPoint));
  }

  @Test
  public void testGetParseGeoPointKeyExistValueNull() throws Exception {
    ParseGeoPoint geoPoint = new ParseGeoPoint(45.484, 27.029);
    final Map<String, Object> params = new HashMap<>();
    params.put("key", JSONObject.NULL);
    params.put("keyAgain", null);

    ParseConfig config = new ParseConfig(params);
    assertNull(config.getParseGeoPoint("key"));
    assertNull(config.getParseGeoPoint("key", geoPoint));
    assertNull(config.getParseGeoPoint("keyAgain"));
    assertNull(config.getParseGeoPoint("keyAgain", geoPoint));
  }

  //endregion

  //region testGetParseFile

  @Test
  public void testGetParseFileKeyExist() throws Exception {
    final ParseFile file = new ParseFile(
        new ParseFile.State.Builder().name("image.png").url("http://yarr.com/image.png").build());
    ParseFile fileAgain = new ParseFile(
        new ParseFile.State.Builder().name("file.txt").url("http://yarr.com/file.txt").build());
    final Map<String, Object> params = new HashMap<>();
    params.put("key", file);

    ParseConfig config = new ParseConfig(params);
    ParseFile fileConfig = config.getParseFile("key");
    assertEquals(file.getName(), fileConfig.getName());
    assertEquals(file.getUrl(), fileConfig.getUrl());
    assertSame(fileConfig, config.getParseFile("key", fileAgain));
  }

  @Test
  public void testGetParseFileKeyNotExist() throws Exception {
    final ParseFile file = new ParseFile(
        new ParseFile.State.Builder().name("image.png").url("http://yarr.com/image.png").build());
    ParseFile fileAgain = new ParseFile(
        new ParseFile.State.Builder().name("file.txt").url("http://yarr.com/file.txt").build());
    final Map<String, Object> params = new HashMap<>();
    params.put("key", file);

    ParseConfig config = new ParseConfig(params);
    assertNull(config.getParseFile("wrongKey"));
    assertSame(fileAgain, config.getParseFile("wrongKey", fileAgain));
  }

  @Test
  public void testGetParseFileKeyExistValueNotParseFile() throws Exception {
    ParseFile file = new ParseFile(
        new ParseFile.State.Builder().name("file.txt").url("http://yarr.com/file.txt").build());
    final Map<String, Object> params = new HashMap<>();
    params.put("key", 1);

    ParseConfig config = new ParseConfig(params);
    assertNull(config.getParseFile("key"));
    assertSame(file, config.getParseFile("key", file));
  }

  @Test
  public void testGetParseFileKeyExistValueNull() throws Exception {
    ParseFile file = new ParseFile(
        new ParseFile.State.Builder().name("file.txt").url("http://yarr.com/file.txt").build());
    final Map<String, Object> params = new HashMap<>();
    params.put("key", JSONObject.NULL);
    params.put("keyAgain", JSONObject.NULL);

    ParseConfig config = new ParseConfig(params);
    assertNull(config.getParseFile("key"));
    assertNull(config.getParseFile("key", file));
    assertNull(config.getParseFile("keyAgain"));
    assertNull(config.getParseFile("keyAgain", file));
  }

  //endregion

  //region testToString

  @Test
  public void testToStringList() throws Exception {
    final List<String> list = new ArrayList<>();
    list.add("foo");
    list.add("bar");
    list.add("baz");

    final Map<String, Object> params = new HashMap<>();
    params.put("list", list);

    ParseConfig config = new ParseConfig(params);
    String configStr = config.toString();
    assertTrue(configStr.contains("ParseConfig"));
    assertTrue(configStr.contains("list"));
    assertTrue(configStr.contains("bar"));
    assertTrue(configStr.contains("baz"));
    assertTrue(configStr.contains("foo"));
  }

  @Test
  public void testToStringMap() throws Exception {
    final Map<String, Object> map = new HashMap<>();
    map.put("first", "foo");
    map.put("second", "bar");
    map.put("third", "baz");
    final Map<String, Object> params = new HashMap<>();
    params.put("map", map);

    ParseConfig config = new ParseConfig(params);
    String configStr = config.toString();
    assertTrue(configStr.contains("ParseConfig"));
    assertTrue(configStr.contains("map"));
    assertTrue(configStr.contains("second=bar"));
    assertTrue(configStr.contains("third=baz"));
    assertTrue(configStr.contains("first=foo"));
  }

  @Test
  public void testToStringParseGeoPoint() throws Exception {
    final ParseGeoPoint geoPoint = new ParseGeoPoint(45.484, 27.029);
    final Map<String, Object> params = new HashMap<>();
    params.put("geoPoint", geoPoint);

    ParseConfig config = new ParseConfig(params);
    String configStr = config.toString();
    assertTrue(configStr.contains("ParseGeoPoint"));
    assertTrue(configStr.contains("45.484"));
    assertTrue(configStr.contains("27.029"));
  }

  //endregion

  private ParseConfigController mockParseConfigControllerWithResponse(final ParseConfig result) {
    ParseConfigController controller = mock(ParseConfigController.class);
    when(controller.getAsync(anyString()))
        .thenReturn(Task.forResult(result));
    return controller;
  }

  private ParseConfigController mockParseConfigControllerWithException(Exception exception) {
    ParseConfigController controller = mock(ParseConfigController.class);
    when(controller.getAsync(anyString()))
        .thenReturn(Task.<ParseConfig>forError(exception));
    return controller;
  }

  private ParseCurrentConfigController mockParseCurrentConfigControllerWithResponse(
      final ParseConfig result) {
    ParseCurrentConfigController controller = mock(ParseCurrentConfigController.class);
    when(controller.getCurrentConfigAsync())
        .thenReturn(Task.forResult(result));
    return controller;
  }

  private ParseCurrentConfigController mockParseCurrentConfigControllerWithException(
      Exception exception) {
    ParseCurrentConfigController controller = mock(ParseCurrentConfigController.class);
    when(controller.getCurrentConfigAsync())
        .thenReturn(Task.<ParseConfig>forError(exception));
    return controller;
  }
}

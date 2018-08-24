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
import org.junit.Test;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.SimpleTimeZone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class ParseObjectCurrentCoderTest {

    // These magic strings are copied from ParseObjectCurrentCoder, since we do not want to make
    // the magic strings in ParseObjectCurrentCoder to be package for tests.
  /*
  /2 format JSON Keys
  */
    private static final String KEY_OBJECT_ID = "objectId";
    private static final String KEY_CLASS_NAME = "classname";
    private static final String KEY_CREATED_AT = "createdAt";
    private static final String KEY_UPDATED_AT = "updatedAt";
    private static final String KEY_DATA = "data";

    /*
    Old serialized JSON keys
     */
    private static final String KEY_OLD_OBJECT_ID = "id";
    private static final String KEY_OLD_CREATED_AT = "created_at";
    private static final String KEY_OLD_UPDATED_AT = "updated_at";
    private static final String KEY_OLD_POINTERS = "pointers";


    @Test
    public void testEncodeSuccess() throws Exception {
        Date createAt = new Date(1000);
        Date updateAt = new Date(2000);
        ParseObject.State state = new ParseObject.State.Builder("Test")
                .createdAt(createAt)
                .updatedAt(updateAt)
                .objectId("objectId")
                .put("key", "value")
                .build();

        ParseObjectCurrentCoder coder = ParseObjectCurrentCoder.get();
        JSONObject objectJson = coder.encode(state, null, PointerEncoder.get());

        assertEquals("Test", objectJson.getString(KEY_CLASS_NAME));
        JSONObject dataJson = objectJson.getJSONObject(KEY_DATA);
        String createAtStr = ParseDateFormat.getInstance().format(createAt);
        assertEquals(createAtStr, dataJson.getString(KEY_CREATED_AT));
        String updateAtStr = ParseDateFormat.getInstance().format(updateAt);
        assertEquals(updateAtStr, dataJson.getString(KEY_UPDATED_AT));
        assertEquals("objectId", dataJson.getString(KEY_OBJECT_ID));
        assertEquals("value", dataJson.getString("key"));
    }

    @Test
    public void testEncodeSuccessWithEmptyState() throws Exception {
        ParseObject.State state = new ParseObject.State.Builder("Test")
                .build();

        ParseObjectCurrentCoder coder = ParseObjectCurrentCoder.get();
        JSONObject objectJson = coder.encode(state, null, PointerEncoder.get());

        assertEquals("Test", objectJson.getString(KEY_CLASS_NAME));
        JSONObject dataJson = objectJson.getJSONObject(KEY_DATA);
        assertFalse(dataJson.has(KEY_CREATED_AT));
        assertFalse(dataJson.has(KEY_UPDATED_AT));
        assertFalse(dataJson.has(KEY_OBJECT_ID));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testEncodeFailureWithNotNullParseOperationSet() {
        ParseObject.State state = new ParseObject.State.Builder("Test")
                .build();

        ParseObjectCurrentCoder coder = ParseObjectCurrentCoder.get();
        coder.encode(state, new ParseOperationSet(), PointerEncoder.get());
    }

    @Test
    public void testDecodeSuccessWithoutOldFormatJson() throws Exception {
        Date createAt = new Date(1000);
        Date updateAt = new Date(2000);
        String createAtStr = ParseDateFormat.getInstance().format(createAt);
        String updateAtStr = ParseDateFormat.getInstance().format(updateAt);
        JSONObject dataJson = new JSONObject()
                .put(KEY_OBJECT_ID, "objectId")
                .put(KEY_CREATED_AT, createAtStr)
                .put(KEY_UPDATED_AT, updateAtStr)
                .put("key", "value");
        JSONObject objectJson = new JSONObject();
        objectJson.put(KEY_DATA, dataJson);

        ParseObjectCurrentCoder coder = ParseObjectCurrentCoder.get();
        ParseObject.State.Builder builder =
                coder.decode(new ParseObject.State.Builder("Test"), objectJson, ParseDecoder.get());

        // We use the builder to build a state to verify the content in the builder
        ParseObject.State state = builder.build();
        assertEquals(createAt.getTime(), state.createdAt());
        assertEquals(updateAt.getTime(), state.updatedAt());
        assertEquals("objectId", state.objectId());
        assertEquals("value", state.get("key"));
    }

    @Test
    public void testDecodeSuccessWithOldFormatJson() throws Exception {
        Date createAt = new Date(1000);
        Date updateAt = new Date(2000);
        String createAtStr = ParseImpreciseDateFormat.getInstance().format(createAt);
        String updateAtStr = ParseImpreciseDateFormat.getInstance().format(updateAt);
        JSONObject pointerJson = new JSONObject();
        JSONArray innerObjectJson = new JSONArray()
                .put(0, "innerObject")
                .put(1, "innerObjectId");
        pointerJson.put("inner", innerObjectJson);
        JSONObject oldObjectJson = new JSONObject()
                .put(KEY_OLD_OBJECT_ID, "objectId")
                .put(KEY_OLD_CREATED_AT, createAtStr)
                .put(KEY_OLD_UPDATED_AT, updateAtStr)
                .put(KEY_OLD_POINTERS, pointerJson);

        ParseObjectCurrentCoder coder = ParseObjectCurrentCoder.get();
        ParseObject.State.Builder builder =
                coder.decode(new ParseObject.State.Builder("Test"), oldObjectJson, ParseDecoder.get());

        // We use the builder to build a state to verify the content in the builder
        ParseObject.State state = builder.build();
        assertEquals(createAt.getTime(), state.createdAt());
        assertEquals(updateAt.getTime(), state.updatedAt());
        assertEquals("objectId", state.objectId());
        ParseObject innerObject = (ParseObject) state.get("inner");
        assertEquals("innerObject", innerObject.getClassName());
        assertEquals("innerObjectId", innerObject.getObjectId());
    }

    @Test
    public void testObjectSerializationFormat() throws Exception {
        ParseObject childObject = new ParseObject("child");
        childObject.setObjectId("childObjectId");

        DateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        format.setTimeZone(new SimpleTimeZone(0, "GMT"));

        String dateString = "2011-08-12T01:06:05Z";
        Date date = format.parse(dateString);

        String jsonString = "{" +
                "'id':'wnAiJVI3ra'," +
                "'updated_at':'" + dateString + "'," +
                "'pointers':{'child':['child','" + childObject.getObjectId() + "']}," +
                "'classname':'myClass'," +
                "'dirty':true," +
                "'data':{'foo':'bar'}," +
                "'created_at':'2011-08-12T01:06:05Z'," +
                "'deletedKeys':['toDelete']" +
                "}";

        ParseObjectCurrentCoder coder = ParseObjectCurrentCoder.get();
        JSONObject json = new JSONObject(jsonString);
        ParseObject.State state = coder.decode(
                new ParseObject.State.Builder("Test"), json, ParseDecoder.get()).build();

        assertEquals("wnAiJVI3ra", state.objectId());
        assertEquals("bar", state.get("foo"));
        assertEquals(date.getTime(), state.createdAt());
        assertEquals(((ParseObject) state.get("child")).getObjectId(), childObject.getObjectId());

        // Test that objects can be serialized and deserialized without timestamps
        String jsonStringWithoutTimestamps = "{" +
                "'id':'wnAiJVI3ra'," +
                "'pointers':{'child':['child','" + childObject.getObjectId() + "']}," +
                "'classname':'myClass'," +
                "'dirty':true," +
                "'data':{'foo':'bar'}," +
                "'deletedKeys':['toDelete']" +
                "}";

        json = new JSONObject(jsonStringWithoutTimestamps);
        state = coder.decode(
                new ParseObject.State.Builder("Test"), json, ParseDecoder.get()).build();
        assertEquals("wnAiJVI3ra", state.objectId());
    }
}

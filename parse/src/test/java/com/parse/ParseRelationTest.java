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
import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.skyscreamer.jsonassert.JSONCompareMode;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.skyscreamer.jsonassert.JSONAssert.assertEquals;

@RunWith(RobolectricTestRunner.class)
@Config(constants = BuildConfig.class, sdk = TestHelper.ROBOLECTRIC_SDK_VERSION)
public class ParseRelationTest {

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    //region testConstructor

    @Test
    public void testConstructorWithParentAndKey() {
        ParseObject parent = mock(ParseObject.class);
        String key = "test";

        ParseRelation relation = new ParseRelation(parent, key);

        assertEquals(key, relation.getKey());
        assertSame(parent, relation.getParent());
        assertNull(relation.getTargetClass());
    }

    @Test
    public void testConstructorWithTargetClass() {
        String targetClass = "Test";

        ParseRelation relation = new ParseRelation(targetClass);

        assertNull(relation.getKey());
        assertNull(relation.getParent());
        assertEquals(targetClass, relation.getTargetClass());
    }

    @Test
    public void testConstructorWithJSONAndDecoder() throws Exception {
        // Make ParseRelation JSONArray
        ParseObject object = mock(ParseObject.class);
        when(object.getClassName()).thenReturn("Test");
        when(object.getObjectId()).thenReturn("objectId");
        object.setObjectId("objectId");
        JSONArray objectJSONArray = new JSONArray();
        objectJSONArray.put(PointerEncoder.get().encode(object));
        JSONObject relationJSON = new JSONObject();
        relationJSON.put("className", "Test");
        relationJSON.put("objects", objectJSONArray);

        ParseRelation relationFromJSON = new ParseRelation(relationJSON, ParseDecoder.get());

        assertEquals("Test", relationFromJSON.getTargetClass());
        assertEquals(1, relationFromJSON.getKnownObjects().size());
        Object[] objects = relationFromJSON.getKnownObjects().toArray();
        assertEquals("objectId", ((ParseObject) objects[0]).getObjectId());
    }

    //endregion

    //region testParcelable

    @Test
    public void testParcelable() {
        ParseFieldOperations.registerDefaultDecoders();
        ParseRelation<ParseObject> relation = new ParseRelation<>("Test");
        ParseObject parent = new ParseObject("Parent");
        parent.setObjectId("parentId");
        relation.ensureParentAndKey(parent, "key");
        ParseObject inner = new ParseObject("Test");
        inner.setObjectId("innerId");
        relation.add(inner);

        Parcel parcel = Parcel.obtain();
        relation.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        //noinspection unchecked
        ParseRelation<ParseObject> newRelation = ParseRelation.CREATOR.createFromParcel(parcel);
        assertEquals(newRelation.getTargetClass(), "Test");
        assertEquals(newRelation.getKey(), "key");
        assertEquals(newRelation.getParent().getClassName(), "Parent");
        assertEquals(newRelation.getParent().getObjectId(), "parentId");
        assertEquals(newRelation.getKnownObjects().size(), 1);

        // This would fail assertTrue(newRelation.hasKnownObject(inner)).
        // That is because ParseRelation uses == to check for known objects.
    }

    //endregion

    //region testEnsureParentAndKey

    @Test
    public void testEnsureParentAndKey() {
        ParseRelation relation = new ParseRelation("Test");

        ParseObject parent = mock(ParseObject.class);
        relation.ensureParentAndKey(parent, "key");

        assertEquals(parent, relation.getParent());
        assertEquals("key", relation.getKey());
    }

    @Test
    public void testEnsureParentAndKeyWithDifferentParent() {
        ParseRelation relation = new ParseRelation(mock(ParseObject.class), "key");

        thrown.expect(IllegalStateException.class);
        thrown.expectMessage(
                "Internal error. One ParseRelation retrieved from two different ParseObjects.");

        relation.ensureParentAndKey(new ParseObject("Parent"), "key");
    }

    @Test
    public void testEnsureParentAndKeyWithDifferentKey() {
        ParseObject parent = mock(ParseObject.class);
        ParseRelation relation = new ParseRelation(parent, "key");

        thrown.expect(IllegalStateException.class);
        thrown.expectMessage(
                "Internal error. One ParseRelation retrieved from two different keys.");

        relation.ensureParentAndKey(parent, "keyAgain");
    }

    //endregion

    //region testAdd

    @Test
    public void testAdd() {
        ParseObject parent = new ParseObject("Parent");
        ParseRelation relation = new ParseRelation(parent, "key");

        ParseObject object = new ParseObject("Test");
        relation.add(object);

        // Make sure targetClass is updated
        assertEquals("Test", relation.getTargetClass());
        // Make sure object is added to knownObjects
        assertTrue(relation.hasKnownObject(object));
        // Make sure parent is updated
        ParseRelation relationInParent = parent.getRelation("key");
        assertEquals("Test", relationInParent.getTargetClass());
        assertTrue(relationInParent.hasKnownObject(object));

    }

    //endregion

    //region testRemove

    @Test
    public void testRemove() {
        ParseObject parent = new ParseObject("Parent");
        ParseRelation relation = new ParseRelation(parent, "key");

        ParseObject object = new ParseObject("Test");
        relation.add(object);

        relation.remove(object);

        // Make sure targetClass does not change
        assertEquals("Test", relation.getTargetClass());
        // Make sure object is removed from knownObjects
        assertFalse(relation.hasKnownObject(object));
        // Make sure parent is updated
        ParseRelation relationInParent = parent.getRelation("key");
        assertEquals("Test", relationInParent.getTargetClass());
        assertFalse(relation.hasKnownObject(object));
    }

    //endregion

    //region testGetQuery

    @Test
    public void testGetQueryWithNoTargetClass() {
        ParseObject parent = new ParseObject("Parent");
        ParseRelation relation = new ParseRelation(parent, "key");

        ParseQuery query = relation.getQuery();

        // Make sure className is correct
        assertEquals("Parent", query.getClassName());
        ParseQuery.State state = query.getBuilder().build();
        // Make sure redirectClassNameForKey is set
        assertEquals("key", state.extraOptions().get("redirectClassNameForKey"));
        // Make sure where condition is set
        ParseQuery.RelationConstraint relationConstraint =
                (ParseQuery.RelationConstraint) state.constraints().get("$relatedTo");
        assertEquals("key", relationConstraint.getKey());
        assertSame(parent, relationConstraint.getObject());
    }

    @Test
    public void testGetQueryWithTargetClass() {
        ParseObject parent = new ParseObject("Parent");
        ParseRelation relation = new ParseRelation(parent, "key");
        relation.setTargetClass("targetClass");

        ParseQuery query = relation.getQuery();

        // Make sure className is correct
        assertEquals("targetClass", query.getClassName());
        ParseQuery.State state = query.getBuilder().build();
        // Make sure where condition is set
        ParseQuery.RelationConstraint relationConstraint =
                (ParseQuery.RelationConstraint) state.constraints().get("$relatedTo");
        assertEquals("key", relationConstraint.getKey());
        assertSame(parent, relationConstraint.getObject());
    }

    //endregion

    //region testToJSON

    @Test
    public void testEncodeToJSON() throws Exception {
        ParseObject parent = new ParseObject("Parent");
        ParseRelation relation = new ParseRelation(parent, "key");
        relation.setTargetClass("Test");

        ParseObject object = new ParseObject("Test");
        object.setObjectId("objectId");
        relation.addKnownObject(object);

        JSONObject json = relation.encodeToJSON(PointerEncoder.get());

        assertEquals("Relation", json.getString("__type"));
        assertEquals("Test", json.getString("className"));
        JSONArray knownObjectsArray = json.getJSONArray("objects");
        assertEquals(
                (JSONObject) PointerEncoder.get().encode(object),
                knownObjectsArray.getJSONObject(0),
                JSONCompareMode.NON_EXTENSIBLE);
    }

    //endregion
}

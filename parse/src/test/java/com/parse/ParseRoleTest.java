/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThat;

public class ParseRoleTest {

    @Before
    public void setUp() {
        ParseObject.registerSubclass(ParseRole.class);
    }

    @After
    public void tearDown() {
        ParseObject.unregisterSubclass(ParseRole.class);
    }

    //region testConstructor

    @Test
    public void testConstructorWithName() {
        ParseRole role = new ParseRole("Test");

        assertEquals("Test", role.getName());
    }

    @Test
    public void testConstructorWithNameAndACL() {
        ParseACL acl = new ParseACL();

        ParseRole role = new ParseRole("Test", acl);

        assertEquals("Test", role.getName());
        assertSame(acl, role.getACL());
    }

    //endregion

    //region testSetName

    @Test
    public void testSetName() {
        ParseRole role = new ParseRole();

        role.setName("Test");

        assertEquals("Test", role.getName());
    }

    //endregion

    //region testGetUsers

    @Test
    public void testGetUsers() {
        ParseRole role = new ParseRole("Test");

        assertThat(role.getUsers(), instanceOf(ParseRelation.class));
        assertSame(role.getUsers(), role.getRelation("users"));
    }

    //endregion

    //region testGetRoles

    @Test
    public void testGetRoles() {
        ParseRole role = new ParseRole("Test");

        assertThat(role.getRoles(), instanceOf(ParseRelation.class));
        assertSame(role.getRoles(), role.getRelation("roles"));
    }

    //endregion

    //region testValidateSave

    @Test
    public void testValidateSaveSuccess() {
        ParseRole role = new ParseRole("Test");

        role.validateSave();
    }

    @Test
    public void testValidateSaveSuccessWithNoName() {
        ParseRole role = new ParseRole("Test");
        role.setObjectId("test");

        // objectId != null and name == null should not fail
        role.validateSave();
    }

    @Test(expected = IllegalStateException.class)
    public void testValidateSaveFailureWithNoObjectIdAndName() {
        ParseRole role = new ParseRole();

        role.validateSave();
    }

    //endregion

    //region testPut

    @Test
    public void testPutSuccess() {
        ParseRole role = new ParseRole("Test");

        role.put("key", "value");

        assertEquals("value", role.get("key"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testPutFailureWithNameAndObjectIdSet() {
        ParseRole role = new ParseRole("Test");
        role.setObjectId("objectId");

        role.put("name", "value");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testPutFailureWithInvalidNameTypeSet() {
        ParseRole role = new ParseRole("Test");

        role.put("name", 1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testPutFailureWithInvalidNameValueSet() {
        ParseRole role = new ParseRole("Test");

        role.put("name", "!!!!");
    }

    //endregion

    //region testGetQuery

    @Test
    public void testGetQuery() {
        ParseQuery<?> query = ParseRole.getQuery();

        assertEquals(ParseCorePlugins.getInstance().getSubclassingController().getClassName(ParseRole.class), query.getBuilder().getClassName());
    }

    //endregion
}

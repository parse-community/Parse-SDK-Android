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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;

public class SubclassTest extends ResetPluginsParseTest {
    @Before
    public void setUp() throws Exception {
        super.setUp();
        ParseObject.registerParseSubclasses();
        ParseObject.registerSubclass(Person.class);
        ParseObject.registerSubclass(ClassWithDirtyingConstructor.class);
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
        ParseObject.unregisterParseSubclasses();
        ParseObject.unregisterSubclass(Person.class);
        ParseObject.unregisterSubclass(ClassWithDirtyingConstructor.class);
    }

    @SuppressWarnings("unused")
    public void testUnregisteredConstruction() {
        Exception thrown = null;
        try {
            new UnregisteredClass();
        } catch (Exception e) {
            thrown = e;
        }
        assertNotNull(thrown);
        ParseObject.registerSubclass(UnregisteredClass.class);
        try {
            new UnregisteredClass();
        } finally {
            ParseObject.unregisterSubclass(UnregisteredClass.class);
        }
    }

    @Test
    public void testSubclassPointers() {
        Person flashPointer = (Person) ParseObject.createWithoutData("Person", "someFakeObjectId");
        assertFalse(flashPointer.isDirty());
    }

    @Test
    public void testDirtyingConstructorsThrow() {
        ClassWithDirtyingConstructor dirtyObj = new ClassWithDirtyingConstructor();
        assertTrue(dirtyObj.isDirty());
        try {
            ParseObject.createWithoutData("ClassWithDirtyingConstructor", "someFakeObjectId");
            fail("Should throw due to subclass with dirtying constructor");
        } catch (IllegalStateException e) {
            // success
        }
    }

    @Test
    public void testRegisteringSubclassesUsesMostDescendantSubclass() {
        try {
            // When we register a ParseUser subclass, we have to clear the cached currentParseUser, so
            // we need to register a mock ParseUserController here, otherwise Parse.getCacheDir() will
            // throw an exception in unit test environment.
            ParseCurrentUserController controller = mock(ParseCurrentUserController.class);
            ParseCorePlugins.getInstance().registerCurrentUserController(controller);
            assertEquals(ParseUser.class, ParseObject.create("_User").getClass());
            ParseObject.registerSubclass(MyUser.class);
            assertEquals(MyUser.class, ParseObject.create("_User").getClass());
            ParseObject.registerSubclass(ParseUser.class);
            assertEquals(MyUser.class, ParseObject.create("_User").getClass());

            // This is expected to fail as MyUser2 and MyUser are not directly related.
            try {
                ParseObject.registerSubclass(MyUser2.class);
                fail();
            } catch (IllegalArgumentException ex) {
                /* expected */
            }

            assertEquals(MyUser.class, ParseObject.create("_User").getClass());
        } finally {
            ParseObject.unregisterSubclass(ParseUser.class);
            ParseCorePlugins.getInstance().reset();
        }
    }

    @Test
    public void testRegisteringClassWithNoDefaultConstructorThrows() {
        Exception thrown = null;
        try {
            ParseObject.registerSubclass(NoDefaultConstructor.class);
        } catch (Exception e) {
            thrown = e;
        }
        assertNotNull(thrown);
    }

    /**
     * This is a subclass of ParseObject that will be used below. We're going to imagine a world in
     * which every "Person" is an instance of "The Flash".
     */
    @ParseClassName("Person")
    public static class Person extends ParseObject {
        public static ParseQuery<Person> getQuery() {
            return ParseQuery.getQuery(Person.class);
        }

        public String getNickname() {
            return getString("nickname");
        }

        public void setNickname(String name) {
            put("nickname", name);
        }

        public String getRealName() {
            return getString("realName");
        }

        public void setRealName(String name) {
            put("realName", name);
        }

        @Override
        void setDefaultValues() {
            setNickname("The Flash");
        }
    }

    @ParseClassName("NoDefaultConstructor")
    public static class NoDefaultConstructor extends ParseObject {
        public NoDefaultConstructor(Void argument) {
        }
    }

    @ParseClassName("ClassWithDirtyingConstructor")
    public static class ClassWithDirtyingConstructor extends ParseObject {
        public ClassWithDirtyingConstructor() {
            put("foo", "Bar");
        }
    }

    @ParseClassName("UnregisteredClass")
    public static class UnregisteredClass extends ParseObject {
    }

    public static class MyUser extends ParseUser {
    }

    public static class MyUser2 extends ParseUser {
    }
}
